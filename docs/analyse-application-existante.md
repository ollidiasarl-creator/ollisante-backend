# Analyse de la version de test iOS « Ollisante » (build 1.1)

Source : le paquet iOS compilé fourni (`Ollisante_app.zip`). Le code source n'est pas inclus : seul le bundle
JavaScript compilé (bytecode Hermes) a pu être examiné, de façon non intrusive, à partir de ses chaînes de texte.
Les noms de routes ci-dessous sont déduits de ces chaînes ; leurs limites exactes et les formats de requête
et de réponse n'ont **pas** pu être reconstitués. À confirmer avec le développeur.

## Identité technique
- Bundle iOS : `com.idrissadiarra.ollisante`, version 1.1, iOS 18 minimum.
- React Native (architecture récente, Hermes), React Navigation, Reanimated, Axios.
- Bilingue français / anglais (drapeaux fr / us).
- Permissions : appareil photo, micro, photothèque (documents, photo de profil, visio prévue).

## API contactée par l'application
Adresse de base : `https://api.ollisante.com/ollisante/…`
Routes repérées : `/auth/login-password`, `/users/inscription`, `/anonyme/inscriptionConfirmation`,
`/anonyme/confirmerRendezVous`, `/paysActuel`, `/structures` (structure), `/prendreRendezVousParLePatient`,
`/modifierRendezVousParLePatient`, `annulerRendezVous…`, `getRendezVous`, `supprimerDisponibilite`,
`addDisponibiliteParStructure`, `getDisponibilite…`, `getPersonnel`, `getFAQ`, `formationTrouve`,
`actualiteTrouve`, `/ocr/photoDocteur`, `/ocr/pdf`, `verifierPawaPayStatus`.

## Fonctionnalités visibles (libellés)
Inscription et confirmation par code, connexion, mot de passe oublié / changé, recherche de docteur ou
d'hôpital par pays, fiche docteur recommandé / « Top Doctor », prise de rendez-vous (choix des jours et heures),
mes rendez-vous, annulation, espace praticien (ajout et suppression de disponibilités, par structure),
paiement des frais de réservation (PawaPay ; adresse CinetPay présente), annonces, formations
(participation / annulation), FAQ, téléchargement de PDF, lecture OCR de documents / photos de docteur.

## Constats
- **Restes d'autres projets** dans le bundle : « NuméroÉtudiant », « UpdateEtudiantPersonnel », adresses
  `malifoli.org` et `vps82722.serveur-vps.net/malifoli`, `/beti`, une URL de sujet de TP universitaire,
  et l'ancien backend était un projet de gestion scolaire. À nettoyer lors de la reconstruction.
- **Aucune clé secrète lisible** n'a été trouvée dans le bundle (analyse limitée : le code compilé ne permet pas
  une revue complète).
- Les paiements d'essai (« payment_method test ») dans la base montrent que le paiement n'était pas finalisé.
- Le backend en production (`api.ollisante.com`) est l'ancien serveur Java ; la nouvelle application ne
  pourra l'utiliser qu'après migration ou en le remplaçant par ce backend.

## Écart avec le nouveau backend
| Fonction de l'application | État dans le nouveau backend |
|---|---|
| Connexion / inscription | ✔ (e-mail ou téléphone) ; confirmation par code et mot de passe oublié : à faire |
| Pays, spécialités | ✔ |
| Recherche de praticiens et structures | ✔ praticiens et établissements |
| Disponibilités, créneaux, rendez-vous, annulation | ✔ |
| Paiement PawaPay / CinetPay | à faire (étape 3) |
| Annonces, formations, FAQ | à faire (étape 6) |
| OCR documents / photo de docteur | à décider : vérification manuelle par un admin en place, OCR facultatif |
| Visioconsultation, chat, documents | à faire (étape 4) |
