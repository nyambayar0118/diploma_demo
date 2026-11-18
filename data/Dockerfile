# Tools for the data milestone:
#   osm2pgsql + psql  -> import OSM places into PostGIS   (data/import.sh)
#   pmtiles (go-pmtiles) -> cut Ulaanbaatar vector tiles   (data/tiles.sh)
FROM ubuntu:24.04
ARG PMTILES_VERSION=1.30.1
RUN apt-get update \
 && apt-get install -y --no-install-recommends osm2pgsql postgresql-client curl ca-certificates \
 && rm -rf /var/lib/apt/lists/* \
 && curl -fsSL -o /tmp/pmtiles.tar.gz \
      "https://github.com/protomaps/go-pmtiles/releases/download/v${PMTILES_VERSION}/go-pmtiles_${PMTILES_VERSION}_Linux_x86_64.tar.gz" \
 && tar -xzf /tmp/pmtiles.tar.gz -C /usr/local/bin pmtiles \
 && rm /tmp/pmtiles.tar.gz
WORKDIR /data
ENTRYPOINT ["bash"]
