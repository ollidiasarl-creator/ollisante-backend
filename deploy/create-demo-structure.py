#!/usr/bin/env python3
"""Crée le compte « structure » de démonstration et range son mot de passe dans le fichier local des comptes de test.

À lancer depuis le Mac :
    python3 "/Users/mac/Downloads/ollisante-backend 2/deploy/create-demo-structure.py"

Ensuite, prévenez Claude : il valide la structure et y rattache les 5 médecins de démonstration du Sénégal.
Le mot de passe ne s'affiche jamais à l'écran.
"""
import json
import os
import secrets
import string
import urllib.error
import urllib.request
from datetime import datetime

API = os.environ.get("OLLISANTE_API", "https://api-test.ollisante.com")
OUT_FILE = os.environ.get("OLLISANTE_OUT_FILE", os.path.expanduser("~/Documents/ollisante-comptes-test.txt"))
EMAIL = "structure.demo@test.ollisante.com"


def password():
    alphabet = "".join(c for c in string.ascii_letters + string.digits if c not in "0O1lI")
    return "-".join("".join(secrets.choice(alphabet) for _ in range(4)) for _ in range(3))


def main():
    pwd = password()
    body = {
        "type": "CLINIC", "name": "Groupe Kër Ollisanté (démo)", "countryCode": "SN", "city": "Dakar",
        "address": "Avenue Léopold Sédar Senghor, Plateau", "email": EMAIL, "phone": "+221330000001",
        "domain": "", "registrationNumber": "DEMO-SN-0001", "contactName": "Équipe de démonstration",
        "contactRole": "Direction", "beds": 40, "password": pwd, "locale": "fr",
    }
    req = urllib.request.Request(API + "/api/v1/auth/register/structure", data=json.dumps(body).encode(),
                                 headers={"Content-Type": "application/json"}, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=30):
            pass
    except urllib.error.HTTPError as e:
        message = json.loads(e.read() or b"{}").get("message", "")
        if e.code == 409:
            print("Le compte structure de démonstration existe déjà : rien à faire (mot de passe inchangé).")
            return
        raise SystemExit(f"Création refusée ({e.code}) : {message}")

    with open(OUT_FILE, "a", encoding="utf-8") as out:
        out.write(f"\n===== Structure de démonstration — {datetime.now():%d/%m/%Y %H:%M} =====\n")
        out.write(f"Espace Structure : {API}/structure/ (ou profil Structure dans l'application)\n")
        out.write(f"Structure Groupe Kër Ollisanté (démo) : {EMAIL} / {pwd}\n")
    os.chmod(OUT_FILE, 0o600)
    print("Terminé : compte structure de démonstration créé.")
    print(f"Identifiants rangés dans : {OUT_FILE}")
    print("Prévenez Claude pour qu'il valide la structure et y rattache les médecins.")


if __name__ == "__main__":
    main()
