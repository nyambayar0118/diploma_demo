#!/usr/bin/env bash
# Cuts the Ulaanbaatar area out of the latest Protomaps daily world basemap build
# (OpenStreetMap data) into data/tiles/ulaanbaatar.pmtiles. Only the needed tiles are
# downloaded (HTTP range requests), typically a few tens of MB.
set -euo pipefail

BBOX="${BBOX:-106.55,47.75,107.25,48.05}"
MAXZOOM="${MAXZOOM:-15}"
OUT="/data/tiles/ulaanbaatar.pmtiles"

# Daily builds are named YYYYMMDD.pmtiles and kept for a limited time: try the last 10 days.
BUILD_URL=""
for i in $(seq 1 10); do
  d=$(date -u -d "-$i day" +%Y%m%d)
  url="https://build.protomaps.com/$d.pmtiles"
  if curl -fsI "$url" > /dev/null; then BUILD_URL="$url"; break; fi
done
if [ -z "$BUILD_URL" ]; then
  echo "!! No Protomaps build found in the last 10 days. Check https://maps.protomaps.com/builds/" >&2
  exit 1
fi

echo ">> Extracting $BBOX (max zoom $MAXZOOM) from $BUILD_URL"
mkdir -p /data/tiles
pmtiles extract "$BUILD_URL" "$OUT.part" --bbox="$BBOX" --maxzoom="$MAXZOOM"
mv "$OUT.part" "$OUT"
pmtiles show "$OUT" | head -20
echo ">> Done: $OUT"
