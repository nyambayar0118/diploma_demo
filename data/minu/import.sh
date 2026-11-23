#!/usr/bin/env bash
# Minu Map-аас (map.minu.mn) газрыг OSM-ийн импорт дээр нэмж импортлоно.
#   1. data/minu/fetch.py бүх компанийг Minu API-аар татна (нэг удаа; шинэчлэхэд MINU_REFETCH=1)
#   2. data/sql/load_minu.sql манай ангилалд орох Улаанбаатарын газрыг үлдээж, таарсан OSM газрыг
#      баяжуулж, бусдыг source = 'MINU'-тай нэмнэ
# osm-import-ийн дараа docker compose-оор ажиллуулна (docs/DEPLOY.md-г үз). .env-д MINU_API_TOKEN хэрэгтэй.
set -euo pipefail

export MINU_OUT="${MINU_OUT:-/data/raw/minu/companies.jsonl}"

if [ -z "${MINU_API_TOKEN:-}" ]; then
  echo "!! MINU_API_TOKEN is not set (add it to .env)" >&2
  exit 1
fi

if [ "${MINU_REFETCH:-0}" = "1" ]; then
  rm -f "$MINU_OUT.done"
  export MINU_FRESH=1
fi
if [ ! -f "$MINU_OUT.done" ]; then
  # MINU_FRESH=1 биш бол fetch.py тасарсан татан авалтыг үргэлжлүүлнэ
  echo ">> Downloading Minu companies (about 8 minutes at one request per second)"
  python3 /data/minu/fetch.py
else
  echo ">> Using the earlier download $MINU_OUT (MINU_REFETCH=1 downloads again)"
fi

echo ">> Waiting for the database"
until pg_isready -q; do sleep 1; done

if ! psql -tAc "SELECT to_regclass('public.minu_category_mappings')" | grep -q minu; then
  echo "!! Table minu_category_mappings is missing. Restart the backend once so Flyway runs V15." >&2
  exit 1
fi

echo ">> Loading into places"
psql -f /data/sql/load_minu.sql < "$MINU_OUT"
echo ">> Done"
