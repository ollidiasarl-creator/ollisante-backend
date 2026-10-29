#!/usr/bin/env python3
"""Crée les comptes de test sur un serveur Ollisanté et range les mots de passe dans un fichier local.

Par pays : un établissement test et 5 médecins validés (spécialités différentes, horaires du lundi au
vendredi, 9 h - 13 h). En plus : 5 patients. Les mots de passe ne s'affichent jamais à l'écran.

À lancer depuis le Mac :
    python3 "/Users/mac/Downloads/ollisante-backend 2/deploy/create-test-accounts.py"

Relançable sans risque : un compte qui existe déjà est laissé tel quel.
"""
import json
import os
import secrets
import string
import sys
import urllib.error
import urllib.request
from datetime import datetime

API = os.environ.get("OLLISANTE_API", "https://api-test.ollisante.com")
ADMIN_FILE = os.environ.get("OLLISANTE_ADMIN_FILE", os.path.expanduser("~/Documents/ollisante-admin-test.txt"))
OUT_FILE = os.environ.get("OLLISANTE_OUT_FILE", os.path.expanduser("~/Documents/ollisante-comptes-test.txt"))
DOMAIN = "test.ollisante.com"  # adresses fictives : aucun e-mail n'y est réellement lu

# Pays de lancement : (code, ville de l'établissement test, prénoms, noms)
COUNTRIES = [
    ("SN", "Dakar", ["Aminata", "Moussa", "Fatou", "Cheikh", "Awa"], ["Diop", "Ndiaye", "Fall", "Sarr", "Sow"]),
    ("CI", "Abidjan", ["Aya", "Koffi", "Mariam", "Yao", "Adjoua"], ["Kouassi", "Konan", "Traoré", "Kouamé", "Bamba"]),
    ("BJ", "Cotonou", ["Rachida", "Codjo", "Afi", "Sèna", "Mariam"], ["Houngbo", "Agbo", "Adjovi", "Dossou", "Zinsou"]),
    ("GN", "Conakry", ["Mariama", "Alpha", "Kadiatou", "Ibrahima", "Hawa"], ["Diallo", "Barry", "Camara", "Bah", "Soumah"]),
    ("CD", "Kinshasa", ["Grâce", "Patrick", "Ruth", "Jean-Marc", "Esther"], ["Mukendi", "Kabila", "Ilunga", "Tshibanda", "Mbuyi"]),
    ("FR", "Paris", ["Claire", "Thomas", "Sophie", "Julien", "Inès"], ["Martin", "Bernard", "Dubois", "Moreau", "Laurent"]),
    ("CH", "Genève", ["Léa", "Lukas", "Chloé", "Marc", "Nora"], ["Müller", "Favre", "Rochat", "Meier", "Bonvin"]),
]

# Médecine générale partout, puis 4 autres spécialités qui varient d'un pays à l'autre.
OTHER_SPECIALTIES = ["PEDIATRICS", "GYNECOLOGY", "CARDIOLOGY", "DERMATOLOGY", "OPHTHALMOLOGY", "ENT",
                     "DENTISTRY", "PSYCHIATRY", "NEUROLOGY", "ORTHOPEDICS", "NUTRITION", "UROLOGY",
                     "RADIOLOGY", "ANESTHESIOLOGY"]

PATIENTS = [("Khadija", "Ba", "SN"), ("Serge", "Yao", "CI"), ("Nadège", "Ahouansou", "BJ"),
            ("Oumou", "Sylla", "GN"), ("Christian", "Kalala", "CD")]


def password():
    alphabet = string.ascii_letters + string.digits
    alphabet = "".join(c for c in alphabet if c not in "0O1lI")
    return "-".join("".join(secrets.choice(alphabet) for _ in range(4)) for _ in range(3))


def call(method, path, body=None, token=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(API + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=30) as res:
            raw = res.read()
            return res.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as e:
        raw = e.read()
        try:
            return e.code, json.loads(raw)
        except ValueError:
            return e.code, {"message": raw.decode(errors="replace")}


def admin_credentials():
    email = pwd = None
    with open(ADMIN_FILE, encoding="utf-8") as f:
        for line in f:
            if line.startswith("Identifiant : "):
                email = line.split(" : ", 1)[1].strip()
            elif line.startswith("Mot de passe : "):
                pwd = line.split(" : ", 1)[1].rstrip("\n")
    if not email or not pwd:
        sys.exit(f"Identifiants administrateur introuvables dans {ADMIN_FILE}.")
    return email, pwd


def login(identifier, pwd):
    status, body = call("POST", "/api/v1/auth/login", {"identifier": identifier, "password": pwd})
    if status == 202:
        # Administrateur : code de connexion envoyé par e-mail (double vérification).
        code = input(f"Code reçu par e-mail ({body.get('emailHint')}) : ").strip()
        status, body = call("POST", "/api/v1/auth/login/verify",
                            {"challengeId": body["challengeId"], "code": code, "rememberDevice": False})
        if status != 200:
            raise RuntimeError(f"code refusé ({status}) : {body.get('message')}")
        return body["auth"]["accessToken"]
    if status != 200:
        raise RuntimeError(f"connexion refusée ({status}) : {body.get('message')}")
    return body["accessToken"]


def main():
    admin_email, admin_pwd = admin_credentials()
    admin = login(admin_email, admin_pwd)
    del admin_pwd

    _, specialties = call("GET", "/api/v1/specialties")
    known = {s["code"]: s["nameFr"] for s in specialties}
    _, countries = call("GET", "/api/v1/countries")
    active = {c["code"] for c in countries}

    created, skipped, problems = [], [], []
    out = open(OUT_FILE, "a", encoding="utf-8")
    os.chmod(OUT_FILE, 0o600)
    out.write(f"\n===== Comptes de test {API} — {datetime.now():%d/%m/%Y %H:%M} =====\n")
    out.write("Connexion : profil Médecin ou Patient, puis « Avec mon adresse e-mail ».\n\n")

    for index, (code, city, first_names, last_names) in enumerate(COUNTRIES):
        if code not in active:
            problems.append(f"{code} : pays inactif sur le serveur, ignoré")
            continue
        print(f"{code} …", flush=True)

        # Établissement test du pays (réutilisé s'il existe déjà).
        facility_name = f"Clinique Test Ollisanté {city}"
        _, facilities = call("GET", f"/api/v1/facilities?countryCode={code}")
        facility = next((f for f in facilities if f["name"] == facility_name), None)
        if facility is None:
            status, facility = call("POST", "/api/v1/facilities",
                                    {"name": facility_name, "countryCode": code, "city": city,
                                     "address": "Adresse de test"}, admin)
            if status != 201:
                problems.append(f"{code} : établissement non créé ({facility.get('message')})")
                continue
        out.write(f"--- {code} · établissement : {facility_name}\n")

        start = (index * 4) % len(OTHER_SPECIALTIES)
        codes = ["GENERAL_PRACTICE"] + [OTHER_SPECIALTIES[(start + k) % len(OTHER_SPECIALTIES)] for k in range(4)]
        for n in range(5):
            email = f"medecin.{code.lower()}.{n + 1}@{DOMAIN}"
            pwd = password()
            body = {"firstName": first_names[n], "lastName": last_names[n], "email": email, "phone": "",
                    "countryCode": code, "licenseNumber": f"TEST-{code}-{n + 1:03d}",
                    "specialtyCodes": [codes[n]], "locale": "fr", "password": pwd}
            status, res = call("POST", "/api/v1/admin/practitioners", body, admin)
            if status == 409:
                skipped.append(email)
                out.write(f"Médecin {email} : existait déjà (mot de passe inchangé)\n")
                continue
            if status != 201:
                problems.append(f"{email} : {res.get('message')}")
                continue
            # Horaires : lundi au vendredi, 9 h - 13 h, créneaux de 30 min, sur place.
            token = login(email, pwd)
            for weekday in range(1, 6):
                s, r = call("POST", "/api/v1/me/availability-rules",
                            {"weekday": weekday, "startTime": "09:00", "endTime": "13:00", "slotMinutes": 30,
                             "mode": "IN_PERSON", "facilityId": facility["id"]}, token)
                if s not in (200, 201):
                    problems.append(f"{email} : horaire non créé ({r.get('message')})")
                    break
            label = known.get(codes[n], codes[n])
            out.write(f"Médecin Dr {first_names[n]} {last_names[n]} ({label}) : {email} / {pwd}\n")
            created.append(email)

    out.write("--- Patients\n")
    for n, (first, last, code) in enumerate(PATIENTS):
        email = f"patient.{n + 1}@{DOMAIN}"
        pwd = password()
        # Créé par l'administrateur : pas de code de confirmation à saisir pour les comptes de test.
        status, res = call("POST", "/api/v1/admin/patients",
                           {"email": email, "password": pwd, "firstName": first, "lastName": last,
                            "phone": "", "countryCode": code, "locale": "fr"}, admin)
        if status == 409:
            skipped.append(email)
            out.write(f"Patient {email} : existait déjà (mot de passe inchangé)\n")
            continue
        if status not in (200, 201):
            problems.append(f"{email} : {res.get('message')}")
            continue
        out.write(f"Patient {first} {last} ({code}) : {email} / {pwd}\n")
        created.append(email)
    out.close()

    print(f"\nTerminé : {len(created)} compte(s) créé(s), {len(skipped)} déjà existant(s).")
    for p in problems:
        print("  Attention :", p)
    print(f"Les identifiants sont rangés dans : {OUT_FILE}")


if __name__ == "__main__":
    main()
