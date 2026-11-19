# Ухаалаг маршрут: сонирхолд суурилсан газар санал болгох, маршрут төлөвлөх систем

Bachelor thesis project (О.Нямбаяр, МУИС). Design: thesis chapter 3.

| Folder | What it is |
|---|---|
| `backend/` | Core server: Spring Boot 4, Java 21, modular monolith, Flyway migrations |
| `frontend/` | React + TypeScript PWA, MapLibre GL JS map |
| `ml/` | FastAPI ML scoring service (later: LightGBM training pipeline) |
| `data/` | OSM import and vector tile (PMTiles) scripts |
| `infra/` | `docker-compose.yml`, Nginx (API gateway) config |

## Run everything (Docker)

Requirements: Docker Desktop running, Git.

```powershell
git clone https://github.com/nyambayar0118/diploma_demo.git
cd diploma_demo
copy .env.example .env          # then edit DB_PASSWORD in .env
docker compose -f infra/docker-compose.yml --env-file .env up --build
```

Open http://localhost:8080. You should see a map of Ulaanbaatar and a status box
showing `API: UP · DB: UP · ML: UP`.

Stop with `Ctrl+C`; remove containers with
`docker compose -f infra/docker-compose.yml down` (add `-v` to also delete the database).

## Deploy with HTTPS

VPS or home server with a free DuckDNS name and automatic Let's Encrypt certificates: see [docs/DEPLOY.md](docs/DEPLOY.md).

## Import OpenStreetMap places (Ulaanbaatar)

The system's places and buildings come from OpenStreetMap. With the system running
(`up` above, so Flyway has created the tables), in a second terminal:

```powershell
docker compose -f infra/docker-compose.yml --env-file .env run --rm --build osm-import
```

This downloads the Mongolia extract from Geofabrik once (about 100 MB, kept in `data/raw/`),
loads objects inside the Ulaanbaatar bounding box with osm2pgsql (`data/osm/places.lua`),
then maps OSM tags to our categories and fills `places` / `buildings` (`data/sql/load_places.sql`).
It prints a summary per category. Run it again any time to refresh; delete
`data/raw/mongolia-latest.osm.pbf` first to download newer data.

Categories and their OSM tags: `data/categories.py` (generated `V3__seed_categories.sql`, now frozen)
plus later migrations such as `V4__more_category_mappings.sql`. Places appear on the map from zoom level 13.

## Generate our own basemap (Ulaanbaatar vector tiles)

```powershell
docker compose -f infra/docker-compose.yml run --rm --build tiles
```

Cuts Ulaanbaatar out of the latest [Protomaps](https://protomaps.com) daily OpenStreetMap
basemap into `data/tiles/ulaanbaatar.pmtiles` (served by nginx at `/tiles/`). Reload the app
(Ctrl+Shift+R): labels are shown in Mongolian. Without this file the app falls back to the
public OpenFreeMap style.

## Develop one part with hot reload

Keep the database (and ML service) in Docker, run the part you are working on locally:

```powershell
docker compose -f infra/docker-compose.yml --env-file .env up db ml
```

- **Backend:** open `backend/` in IntelliJ IDEA, run `RoutePlannerApplication`
  (set env `DB_PASSWORD` to the value from `.env`). API on http://localhost:8080/api/health
  (stop the `web` container first, both use port 8080).
- **Frontend:** `cd frontend; npm install; npm run dev` → http://localhost:5173
  (`/api` is proxied to http://localhost:8080).
- **ML:** `cd ml; python -m venv .venv; .venv\Scripts\activate; pip install -r requirements.txt;
  uvicorn app.main:app --reload`.
