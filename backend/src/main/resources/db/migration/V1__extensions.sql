-- PostGIS: spatial types, indexes and functions (places, buildings, user locations)
CREATE EXTENSION IF NOT EXISTS postgis;
-- pg_trgm: fuzzy name search in Cyrillic and Latin (АШ01, АШ02)
CREATE EXTENSION IF NOT EXISTS pg_trgm;
