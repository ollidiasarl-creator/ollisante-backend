# Ollisante – Backend (étapes 1 et 2, + connexion par SMS)

API Spring Boot 3.3 / Java 17 / PostgreSQL pour une plateforme de rendez-vous médicaux et de
téléconsultation destinée à l'Afrique.

> **Statut : code écrit mais pas encore compilé ni exécuté.** Le premier lancement est le premier test réel.
> Si une erreur apparaît au démarrage, envoyez-la telle quelle pour correction.

## Prérequis
JDK 17+, Maven 3.9+, Docker

## Lancer en local
```bash
docker compose up -d                                   # PostgreSQL
mvn spring-boot:run -Dspring-boot.run.profiles=local   # API sur http://localhost:8088
mvn test                                               # tests unitaires (JWT, génération de créneaux)
```
Documentation interactive (profil `local`) : http://localhost:8088/swagger-ui.html
Le profil `local` crée un administrateur de test : `admin@ollisante.local` (voir `application-local.yml`).

## Endpoints
| Méthode | Route | Accès |
|---|---|---|
| POST | `/api/v1/auth/register/patient`, `/register/practitioner`, `/login`, `/refresh`, `/logout` | public |
| POST | `/api/v1/auth/otp/request` puis `/api/v1/auth/otp/verify` (connexion ou inscription par téléphone + code SMS) | public |
| GET | `/api/v1/countries`, `/api/v1/specialties` | public |
| GET | `/api/v1/practitioners?countryCode=&specialty=&q=&teleconsultation=&page=&size=` | public |
| GET | `/api/v1/practitioners/{id}` et `/{id}/slots?from=&to=&mode=` | public |
| GET | `/api/v1/facilities?countryCode=` | public |
| POST | `/api/v1/facilities` | praticien ou admin |
| GET/POST/DELETE | `/api/v1/me/availability-rules` | praticien |
| POST | `/api/v1/appointments` | patient |
| GET | `/api/v1/me/appointments` | patient ou praticien |
| POST | `/api/v1/appointments/{id}/cancel` | patient, praticien ou admin |
| GET | `/api/v1/admin/practitioners?status=PENDING` | admin |
| PATCH | `/api/v1/admin/practitioners/{id}/verification` | admin |
| GET | `/api/v1/me` | connecté |

## Parcours de test complet
1. Un praticien s'inscrit (`register/practitioner`) → statut `PENDING`, invisible du public.
2. Un administrateur se connecte, liste les praticiens `PENDING` et passe le praticien à `VERIFIED`.
3. Le praticien ajoute des plages (`POST /me/availability-rules`) : par ex. lundi 09:00–12:00,
   créneaux de 30 min, `VIDEO`. Les heures sont en **heure locale du pays du praticien**.
4. Un patient s'inscrit, cherche le praticien, consulte `/slots`, puis réserve avec le `startsAt` exact d'un créneau.
5. Si deux patients réservent le même créneau, le second reçoit `409 CRENEAU_INDISPONIBLE` (garanti par la base).

## Connexion MVP (voir cahier des charges)
- **Téléphone + SMS** : `otp/request` envoie un code à 6 chiffres (valable 5 min, 5 essais, 1 envoi/min, 5 par jour).
  `otp/verify` connecte le compte ; pour un premier compte, renvoyer aussi `firstName` et `lastName`
  (sinon réponse `400 NOM_REQUIS`, le code reste valable). Aucun mot de passe pour ces comptes.
- **E-mail + mot de passe** : `register/patient` (prénom, nom, e-mail, mot de passe), puis `login`.
- **Envoi des SMS** : en local les SMS s'affichent dans la console. En production, un fournisseur SMS doit être
  branché (interface `SmsSender`) ; tant qu'il manque, l'API répond `503 SMS_NON_CONFIGURE`.

## Tarifs
Les montants communiqués sont prêts dans `docs/tarifs-a-activer.sql`. Ne les exécuter qu'une fois le paiement branché.

## Règles métier déjà en place
- Un créneau est réservable s'il est dans le futur (préavis 60 min), à moins de 60 jours, et libre.
- Tarif vide (par défaut) → rendez-vous `CONFIRMED` ; tarif > 0 → `PENDING_PAYMENT`, annulé automatiquement
  au bout de 30 min sans paiement (le paiement lui-même arrive à l'étape 3).
- Les salles de visio sont des identifiants provisoires, en attendant le choix du fournisseur vidéo.

## Version web
Les navigateurs exigent une autorisation CORS : `ollisante.cors.allowed-origins` (variable `CORS_ALLOWED_ORIGINS`).
Le profil `local` autorise `localhost:8081`, `19006` et `3000`. En production, n'y mettre que l'adresse réelle de l'application web.

## Sécurité
- Mots de passe BCrypt (coût 12) ; jetons d'accès courts (15 min) ; refresh tokens stockés hachés et renouvelés.
- Aucune requête construite par concaténation de chaînes.
- Secret JWT obligatoire (≥ 32 caractères). Un praticien n'est visible qu'après vérification par un admin.

## Production
Voir **`docs/deploiement-lws-vps.md`** (VPS LWS, Nginx, HTTPS, mise en place des clés) et le dossier `deploy/`.

Ne pas utiliser le profil `local`. Définir les variables de `.env.example`, servir l'API derrière HTTPS,
et retirer `BOOTSTRAP_ADMIN_*` après la création du premier administrateur.

## Feuille de route
1. ✔ Socle, schéma, authentification
2. ✔ Recherche de praticiens, disponibilités, créneaux, rendez-vous, vérification des praticiens
3. Paiement mobile money (PawaPay, CinetPay…), reçus, remboursements
4. Téléconsultation vidéo, chat, documents et ordonnances, chiffrement des données médicales
5. Notifications (SMS, e-mail, push), rappels, OTP téléphone, mot de passe oublié
6. Contenus : annonces, formations, FAQ ; profil patient complet
7. Application mobile Expo / React Native, puis tableau de bord administrateur
Voir aussi `docs/analyse-application-existante.md`.
