#!/bin/bash
# Ollisanté : réglages du serveur pour tenir beaucoup de connexions simultanées (2026-10-26).
# À lancer sur le VPS en root. Sauvegarde automatique, et retour arrière automatique si Apache refuse la configuration.
set -euo pipefail

BACKUP=/root/sauvegarde-apache-$(date +%Y-%m-%d-%H%M).tgz
tar czf "$BACKUP" /etc/apache2 /etc/systemd/system/ollisante-api.service
echo "== Sauvegarde : $BACKUP"

# 1. Apache : mode « event » (des milliers de connexions avec peu de mémoire) au lieu de « prefork ».
#    PHP n'est utilisé par aucun site de ce serveur (vérifié : aucun fichier .php), on le désactive.
cat > /etc/apache2/conf-available/ollisante-perf.conf <<'EOF'
# Ollisanté : réglages de charge. Sauvegarde précédente : voir /root/sauvegarde-apache-*.tgz
<IfModule mpm_event_module>
    StartServers             4
    ServerLimit             16
    ThreadsPerChild         64
    MaxRequestWorkers     1024
    MinSpareThreads         64
    MaxSpareThreads        256
    MaxConnectionsPerChild   0
</IfModule>
Timeout 60
ProxyTimeout 60
Protocols h2 http/1.1
# Réponses JSON de l'API compressées
<IfModule mod_deflate.c>
    AddOutputFilterByType DEFLATE application/json image/svg+xml
</IfModule>
# Fichiers du site dont le nom change à chaque version : gardés un an par le navigateur
<LocationMatch "^/(site|admin|structure)/assets/">
    Header set Cache-Control "public, max-age=31536000, immutable"
</LocationMatch>
EOF
a2dismod -q php8.2 mpm_prefork >/dev/null
a2enmod -q mpm_event http2 headers deflate >/dev/null
a2enconf -q ollisante-perf >/dev/null
if ! apachectl configtest; then
  echo "!! Configuration refusée : retour à la sauvegarde"
  tar xzf "$BACKUP" -C /
  exit 1
fi
systemctl restart apache2
echo "== Apache : $(apachectl -V | grep MPM)"

# 2. API : redémarrage automatique dans tous les cas, et arrêt propre (puis redémarrage) si la mémoire manque.
sed -i 's/^Restart=on-failure/Restart=always/' /etc/systemd/system/ollisante-api.service
sed -i 's#^ExecStart=/usr/bin/java -Xms512m -Xmx2g -XX:+UseG1GC -jar#ExecStart=/usr/bin/java -Xms512m -Xmx2g -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError -jar#' /etc/systemd/system/ollisante-api.service
systemctl daemon-reload

# 3. Surveillance : toutes les minutes, si l'API ne répond plus 3 fois de suite, on la redémarre.
cat > /usr/local/bin/ollisante-watchdog.sh <<'EOF'
#!/bin/bash
F=/run/ollisante-watchdog.count
if curl -sf -m 10 http://127.0.0.1:8088/actuator/health >/dev/null; then echo 0 > $F; exit 0; fi
n=$(( $(cat $F 2>/dev/null || echo 0) + 1 )); echo $n > $F
if [ $n -ge 3 ]; then logger -t ollisante-watchdog "API sans réponse, redémarrage"; systemctl restart ollisante-api; echo 0 > $F; fi
EOF
chmod 755 /usr/local/bin/ollisante-watchdog.sh
# Ce serveur n'a pas de démon cron : on planifie avec les minuteurs systemd (sauvegarde 3h15, surveillance chaque minute).
printf '[Unit]\nDescription=Surveillance de l API Ollisante\n[Service]\nType=oneshot\nExecStart=/usr/local/bin/ollisante-watchdog.sh\n' > /etc/systemd/system/ollisante-watchdog.service
printf '[Unit]\nDescription=Surveillance chaque minute\n[Timer]\nOnBootSec=2min\nOnUnitActiveSec=1min\n[Install]\nWantedBy=timers.target\n' > /etc/systemd/system/ollisante-watchdog.timer
printf '[Unit]\nDescription=Sauvegarde quotidienne Ollisante\n[Service]\nType=oneshot\nExecStart=/usr/local/bin/ollisante-backup.sh\n' > /etc/systemd/system/ollisante-backup.service
printf '[Unit]\nDescription=Sauvegarde chaque nuit a 3h15\n[Timer]\nOnCalendar=*-*-* 03:15:00\nPersistent=true\n[Install]\nWantedBy=timers.target\n' > /etc/systemd/system/ollisante-backup.timer
systemctl daemon-reload
systemctl enable --now ollisante-watchdog.timer ollisante-backup.timer

echo "== Terminé"
