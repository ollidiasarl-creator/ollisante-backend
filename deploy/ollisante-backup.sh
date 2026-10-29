#!/bin/bash
# Sauvegarde quotidienne du nouveau Ollisanté (/usr/local/bin/ollisante-backup.sh, minuteur 3h15), 14 jours conservés :
# la base (PostgreSQL) + les fichiers envoyés (images des actualités, pièces justificatives privées).
set -euo pipefail
DIR=/var/backups/ollisante
mkdir -p "$DIR" && chmod 700 "$DIR"
STAMP=$(date +%Y%m%d-%H%M)
FILE="$DIR/ollisante-$STAMP.dump"
cd /tmp && sudo -u postgres pg_dump -Fc "ollisante" > "$FILE.part" && mv "$FILE.part" "$FILE"
chmod 600 "$FILE"
FILES=()
for d in media documents; do [ -d "/opt/ollisante/$d" ] && FILES+=("$d"); done
if [ ${#FILES[@]} -gt 0 ]; then
  tar czf "$DIR/fichiers-$STAMP.tgz.part" -C /opt/ollisante "${FILES[@]}" && mv "$DIR/fichiers-$STAMP.tgz.part" "$DIR/fichiers-$STAMP.tgz"
  chmod 600 "$DIR/fichiers-$STAMP.tgz"
fi
find "$DIR" \( -name "*.dump" -o -name "fichiers-*.tgz" \) -mtime +14 -delete
