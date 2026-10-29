#!/usr/bin/env bash
# Installe le nouveau backend sur le VPS, à l'adresse https://api-test.ollisante.com
# Ne touche PAS à l'ancienne application (Tomcat, MariaDB, ollisante.conf, certificat ollisante.com).
# À exécuter en root sur le serveur. Le mot de passe admin est lu sur l'entrée standard (jamais affiché).
# Relançable sans risque : chaque étape vérifie si elle est déjà faite.
set -euo pipefail

DOMAIN=api-test.ollisante.com
ADMIN_EMAIL=admin-test@ollisante.com
ADMIN_PASSWORD="$(cat)"   # transmis depuis le Mac par ssh, jamais affiché

echo "== 1. PostgreSQL (écoute uniquement sur le serveur ; port 5432 non ouvert au pare-feu)"
if ! dpkg -s postgresql >/dev/null 2>&1; then
  apt-get update -qq
  DEBIAN_FRONTEND=noninteractive apt-get install -y -qq postgresql
fi
systemctl enable --now postgresql

echo "== 2. Compte système et dossiers"
id ollisante >/dev/null 2>&1 || useradd --system --home /opt/ollisante --shell /usr/sbin/nologin ollisante
install -d -o ollisante -g ollisante /opt/ollisante
install -d -m 750 -o root -g ollisante /etc/ollisante

echo "== 3. Base de données et fichier de configuration secret (généré sur le serveur)"
if [ ! -f /etc/ollisante/ollisante.env ]; then
  DBPW="$(openssl rand -hex 24)"
  JWT="$(openssl rand -base64 48 | tr -d '\n')"
  runuser -u postgres -- psql -q -v ON_ERROR_STOP=1 -c "CREATE USER ollisante WITH PASSWORD '${DBPW}';"
  runuser -u postgres -- psql -q -v ON_ERROR_STOP=1 -c "CREATE DATABASE ollisante OWNER ollisante;"
  umask 027
  cat > /etc/ollisante/ollisante.env <<EOF
SERVER_ADDRESS=127.0.0.1
DB_URL=jdbc:postgresql://localhost:5432/ollisante
DB_USER=ollisante
DB_PASSWORD=${DBPW}
JWT_SECRET=${JWT}
BOOTSTRAP_ADMIN_EMAIL=${ADMIN_EMAIL}
BOOTSTRAP_ADMIN_PASSWORD=${ADMIN_PASSWORD}
CORS_ALLOWED_ORIGINS=
EOF
  chown root:ollisante /etc/ollisante/ollisante.env
  chmod 640 /etc/ollisante/ollisante.env
fi

echo "== 4. Application (jar envoyé au préalable dans /tmp/ollisante-backend.jar)"
install -o ollisante -g ollisante -m 640 /tmp/ollisante-backend.jar /opt/ollisante/ollisante-backend.jar
cat > /etc/systemd/system/ollisante-api.service <<'EOF'
[Unit]
Description=Ollisante API (nouveau backend)
After=network.target postgresql.service

[Service]
User=ollisante
Group=ollisante
WorkingDirectory=/opt/ollisante
EnvironmentFile=/etc/ollisante/ollisante.env
ExecStart=/usr/bin/java -Xms512m -Xmx2g -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError -jar /opt/ollisante/ollisante-backend.jar
Restart=always
RestartSec=5
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=full

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable -q ollisante-api
systemctl restart ollisante-api
for i in $(seq 1 60); do curl -fs http://127.0.0.1:8088/actuator/health >/dev/null && break; sleep 2; done
curl -fs http://127.0.0.1:8088/actuator/health || { journalctl -u ollisante-api -n 60 --no-pager; exit 1; }
echo

echo "== 5. Administrateur créé : retrait du mot de passe initial du fichier de configuration"
if grep -q '^BOOTSTRAP_ADMIN' /etc/ollisante/ollisante.env; then
  sed -i '/^BOOTSTRAP_ADMIN/d' /etc/ollisante/ollisante.env
  systemctl restart ollisante-api
  for i in $(seq 1 60); do curl -fs http://127.0.0.1:8088/actuator/health >/dev/null && break; sleep 2; done
fi

echo "== 6. Site Apache séparé pour ${DOMAIN}"
cat > /etc/apache2/sites-available/ollisante-api-test.conf <<EOF
<VirtualHost *:80>
    ServerName ${DOMAIN}
    ProxyPreserveHost On
    ProxyPass / http://127.0.0.1:8088/
    ProxyPassReverse / http://127.0.0.1:8088/
</VirtualHost>
EOF
a2ensite -q ollisante-api-test
apache2ctl configtest
systemctl reload apache2

echo "== 7. Certificat HTTPS distinct pour ${DOMAIN} (le certificat ollisante.com n'est pas modifié)"
if [ ! -d "/etc/letsencrypt/live/${DOMAIN}" ]; then
  certbot --apache --non-interactive --redirect -d "${DOMAIN}" --cert-name "${DOMAIN}"
fi

echo "== Terminé"
curl -fsS "https://${DOMAIN}/actuator/health"; echo
