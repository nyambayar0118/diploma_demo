# Deployment (HTTPS)

Phones only give GPS location to `https://` pages, so the trial needs a public HTTPS address.
The production setup adds **Caddy** in front of the Nginx gateway; Caddy gets and renews a free
Let's Encrypt certificate by itself. It also adds automatic restarts and a daily database backup.

```
Internet ──443──> Caddy (HTTPS) ──> Nginx (web) ──> /api → backend, /tiles, PWA
```

A free **DuckDNS** subdomain (`<name>.duckdns.org`) is enough. No domain has to be bought.

Pick one of the two options.

---

## Option A: a VPS (recommended for the trial)

Always on and has a public IP. Any provider works; 2 vCPU / 2–4 GB RAM / 40 GB disk, Ubuntu 24.04
(roughly $5–8 per month).

1. **DuckDNS:** sign in at https://www.duckdns.org, create a subdomain and set its IP to the VPS IP.
   (The IP of a VPS does not change, so the DuckDNS updater is not needed.)
2. **Install Docker** on the VPS (SSH in as root or with sudo):
   ```bash
   curl -fsSL https://get.docker.com | sh
   ```
3. **Get the code and configure:**
   ```bash
   git clone https://github.com/nyambayar0118/diploma_demo.git && cd diploma_demo
   cp .env.example .env && nano .env    # DB_PASSWORD, JWT_SECRET, DOMAIN
   ```
   Generate secrets with `openssl rand -hex 24`.
4. **Load the data** (downloads OSM; takes a few minutes):
   ```bash
   docker compose -f infra/docker-compose.yml --env-file .env up -d db
   docker compose -f infra/docker-compose.yml --env-file .env run --rm --build osm-import
   docker compose -f infra/docker-compose.yml run --rm --build tiles
   ```
5. **Start everything:**
   ```bash
   docker compose -f infra/docker-compose.yml -f infra/docker-compose.prod.yml --env-file .env up -d --build
   ```
   **Minu Map places** (optional, after the backend has started once so Flyway created V15).
   Put `MINU_API_TOKEN=...` in `.env`, then:
   ```bash
   docker compose -f infra/docker-compose.yml --env-file .env run --rm --build minu-import
   ```
   The first run downloads all Minu companies (~8 min, one request per second) into
   `data/raw/minu/`; later runs reuse that file. To refresh it: add `-e MINU_REFETCH=1`.
   Run it again after every `osm-import`. The summary at the end lists the most common Minu
   categories that map to nothing; add patterns for them to `minu_category_mappings` if needed.
6. Open `https://<name>.duckdns.org`. The first start takes ~30 s while Caddy gets the certificate.

The firewall must allow ports 80 and 443 (`ufw allow 80,443/tcp` if ufw is on).

---

## Option B: the home computer

Free, but the computer has to stay on and awake, and the internet connection must have a real public IP.

1. **Check for CGNAT.** Many Mongolian home connections share one public IP between customers; then
   nothing from outside can reach you. Compare:
   - the router's WAN / Internet IP (router admin page, usually http://192.168.1.1), and
   - the IP shown at https://ifconfig.me
   If they **differ** (or the WAN IP starts with `10.`, `100.64–127.`, `172.16–31.` or `192.168.`), you are
   behind CGNAT. Either ask the ISP for a public (static) IP, or use Option A.
2. **DuckDNS:** create a subdomain and copy the token from https://www.duckdns.org.
3. **Router:** forward TCP ports **80** and **443** to this computer's local IP (give the computer a fixed
   local IP / DHCP reservation in the router first).
4. **Windows:** allow Docker through the firewall when asked; in Docker Desktop settings enable
   "Start Docker Desktop when you sign in"; set Power → Sleep to "Never".
5. **.env:** set `DOMAIN=<name>.duckdns.org`, `DUCKDNS_SUBDOMAIN=<name>`, `DUCKDNS_TOKEN=...` and uncomment
   `COMPOSE_PROFILES=duckdns`.
6. **Start** (PowerShell, from the repo folder):
   ```powershell
   docker compose -f infra/docker-compose.yml -f infra/docker-compose.prod.yml --env-file .env up -d --build
   ```
   Test from a phone on mobile data (not your Wi-Fi): `https://<name>.duckdns.org`.

---

## Everyday commands

The full prefix is long, so on Linux / Git Bash an alias helps:
`alias dc='docker compose -f infra/docker-compose.yml -f infra/docker-compose.prod.yml --env-file .env'`

| What | Command |
|---|---|
| Update to the latest code | `git pull` then `dc up -d --build` |
| Status | `dc ps` |
| Logs (backend) | `dc logs -f --tail 100 backend` |
| HTTPS / certificate problems | `dc logs caddy` |
| Stop | `dc down` (data is kept) |
| Database shell | `dc exec db psql -U routeplanner -d routeplanner` |

**Backups** are written once a day to `backups/` (kept 14 days). Copy them off the machine now and then.
Restore one:
```bash
gunzip -c backups/routeplanner-YYYYMMDD-HHMM.sql.gz | dc exec -T db psql -U routeplanner -d routeplanner
```

Never run `dc down -v`: `-v` deletes the database and certificate volumes.

---

## Google login (optional)

1. https://console.cloud.google.com → create a project → **APIs & Services → OAuth consent screen**:
   External, app name, your e-mail. While it is in "Testing" mode only the test users you add can sign in;
   press **Publish app** for the trial (basic scopes need no Google review).
2. **Credentials → Create credentials → OAuth client ID → Web application.**
   Authorized JavaScript origins: `https://<name>.duckdns.org` and `http://localhost:8080`.
   (No redirect URI is needed: the button returns the token to the page directly.)
3. Put the client id (`....apps.googleusercontent.com`) into `.env` as `GOOGLE_CLIENT_ID` and restart.
   The "Google-ээр үргэлжлүүлэх" button appears on the login and register pages.
