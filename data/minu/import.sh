#!/usr/bin/env bash
# Imports places from Minu Map (map.minu.mn) on top of the OSM import.
#   1. data/minu/fetch.py downloads all companies through the Minu API (once; MINU_REFETCH=1 to refresh)
#   2. data/sql/load_minu.sql keeps Ulaanbaatar places in our categories, enriches matching
#      OSM places and adds the rest with source = 'MINU'
# Run after osm-import, through docker compose (see docs/DEPLOY.md). Needs MINU_API_TOKEN in .env.
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
  # fetch.py continues an interrupted download unless MINU_FRESH=1
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
