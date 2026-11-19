#!/bin/sh
# Daily compressed dump of the database; old dumps are deleted after BACKUP_KEEP_DAYS days.
# Restore:  gunzip -c backups/routeplanner-<date>.sql.gz | docker compose ... exec -T db psql -U <user> -d <db>
set -eu
while true; do
  file="/backups/${PGDATABASE}-$(date +%Y%m%d-%H%M).sql.gz"
  if pg_dump --no-owner --clean --if-exists | gzip > "$file.tmp"; then
    mv "$file.tmp" "$file"
    echo "$(date) backup written: $file"
  else
    rm -f "$file.tmp"
    echo "$(date) backup FAILED" >&2
  fi
  find /backups -name "${PGDATABASE}-*.sql.gz" -mtime +"${BACKUP_KEEP_DAYS}" -delete
  sleep 86400
done
