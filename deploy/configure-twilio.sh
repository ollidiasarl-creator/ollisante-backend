#!/bin/bash
# Enregistre les identifiants Twilio Verify sur api-test, sans les afficher, puis redémarre l'API.
# À lancer depuis le Mac : bash "/Users/mac/Downloads/ollisante-backend 2/deploy/configure-twilio.sh"
set -euo pipefail

read -r -p "Account SID Twilio (commence par AC) : " ACCOUNT_SID

read -r -p "Verify Service SID (commence par VA) : " SERVICE_SID
read -r -s -p "Auth Token Twilio (ne s'affiche pas) : " AUTH_TOKEN
echo

[[ "$ACCOUNT_SID" =~ ^AC[0-9a-fA-F]{32}$ ]] || { echo "L'Account SID doit commencer par AC (34 caractères)."; exit 1; }
[[ "$SERVICE_SID" =~ ^VA[0-9a-fA-F]{32}$ ]] || { echo "Le Verify Service SID doit commencer par VA (34 caractères)."; exit 1; }
[[ "$AUTH_TOKEN" =~ ^[0-9a-fA-F]{32}$ ]] || { echo "L'Auth Token doit faire 32 caractères (chiffres et lettres a-f)."; exit 1; }

# Les secrets passent par l'entrée standard de ssh : ils n'apparaissent ni à l'écran ni dans les commandes.
REMOTE='set -euo pipefail
read -r SID; read -r VA; read -r TOKEN
ENV=/etc/ollisante/ollisante.env
sed -i "/^TWILIO_ACCOUNT_SID=/d;/^TWILIO_AUTH_TOKEN=/d;/^TWILIO_VERIFY_SERVICE_SID=/d" "$ENV"
printf "TWILIO_ACCOUNT_SID=%s\nTWILIO_AUTH_TOKEN=%s\nTWILIO_VERIFY_SERVICE_SID=%s\n" "$SID" "$TOKEN" "$VA" >> "$ENV"
chmod 600 "$ENV"
systemctl restart ollisante-api
for i in $(seq 1 40); do curl -s 127.0.0.1:8088/actuator/health | grep -q UP && break; sleep 3; done
systemctl is-active ollisante-api'

printf '%s\n%s\n%s\n' "$ACCOUNT_SID" "$SERVICE_SID" "$AUTH_TOKEN" |
  ssh -i ~/.ssh/ollisante_vps -o BatchMode=yes root@192.162.70.161 "$REMOTE"

echo "C'est fait : Twilio est enregistré sur le serveur de test."
