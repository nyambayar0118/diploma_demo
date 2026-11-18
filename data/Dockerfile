# Tools for the data milestone: osm2pgsql (OSM -> PostGIS) and psql
FROM ubuntu:24.04
RUN apt-get update \
 && apt-get install -y --no-install-recommends osm2pgsql postgresql-client curl ca-certificates \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /data
ENTRYPOINT ["bash", "/data/import.sh"]
