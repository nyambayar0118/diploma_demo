#!/usr/bin/env bash
# Imports Ulaanbaatar places and buildings from OpenStreetMap into PostGIS.
#   1. downloads the Mongolia extract from Geofabrik (once; delete data/raw/*.pbf to refresh)
#   2. osm2pgsql loads the raw objects into the "osm" staging schema (data/osm/places.lua)
#   3. data/sql/load_places.sql maps tags -> categories and fills places / buildings
# Run through docker compose (see README). Requires the backend to have started once (Flyway tables).
set -euo pipefail

PBF_URL="${PBF_URL:-https://download.geofabrik.de/asia/mongolia-latest.osm.pbf}"
PBF_FILE="${PBF_FILE:-/data/raw/mongolia-latest.osm.pbf}"
# Ulaanbaatar and its surroundings: min lon, min lat, max lon, max lat
BBOX="${BBOX:-106.55,47.75,107.25,48.05}"

mkdir -p /data/raw
if [ ! -f "$PBF_FILE" ]; then
  echo ">> Downloading $PBF_URL"
  curl -fL --retry 3 -o "$PBF_FILE.part" "$PBF_URL"
  mv "$PBF_FILE.part" "$PBF_FILE"
fi

echo ">> Waiting for the database"
until pg_isready -q; do sleep 1; done

if ! psql -tAc "SELECT to_regclass('public.places')" | grep -q places; then
  echo "!! Table 'places' does not exist yet. Start the backend once so Flyway creates it." >&2
  exit 1
fi

echo ">> osm2pgsql: loading OSM objects inside $BBOX"
osm2pgsql --create --output=flex --style=/data/osm/places.lua --bbox="$BBOX" "$PBF_FILE"

echo ">> Filling places and buildings"
psql -f /data/sql/load_places.sql
echo ">> Done"
