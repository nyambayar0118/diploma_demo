# Өгөгдлийн хэрэгслүүд:
#   osm2pgsql + psql  -> OSM-ийн газрыг PostGIS руу импортлох       (data/import.sh)
#   pmtiles (go-pmtiles) -> Улаанбаатарын вектор тайлыг тайрах       (data/tiles.sh)
#   python3 -> Minu Map-ийн газрыг татах                            (data/minu/import.sh)
FROM ubuntu:24.04
ARG PMTILES_VERSION=1.30.1
RUN apt-get update \
 && apt-get install -y --no-install-recommends osm2pgsql postgresql-client curl ca-certificates python3 \
 && rm -rf /var/lib/apt/lists/* \
 && curl -fsSL -o /tmp/pmtiles.tar.gz \
      "https://github.com/protomaps/go-pmtiles/releases/download/v${PMTILES_VERSION}/go-pmtiles_${PMTILES_VERSION}_Linux_x86_64.tar.gz" \
 && tar -xzf /tmp/pmtiles.tar.gz -C /usr/local/bin pmtiles \
 && rm /tmp/pmtiles.tar.gz
WORKDIR /data
ENTRYPOINT ["bash"]
