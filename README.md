# 2bGreen

Decision-support platform built on France's public electricity data (RTE éCO2mix, published through the
ODRÉ open data portal): **when to consume, when to compute, and how each region is doing.**
The same data is presented differently to three personas, and each persona's own data (preferences,
API keys, targets, annotations...) is stored and processed by the platform.

Course project: Ingénierie des Architectures Logicielles, Polytech Nice Sophia.

| Persona | Goal |
|---|---|
| **Citizen** | Use electricity when the grid is clean |
| **DevOps / company** | Run flexible workloads when the grid is clean (carbon-aware computing) |
| **Regional official** | Follow and report on their region's energy transition |

## Status

Environment and project skeletons are in place: infrastructure runs, both backends and the frontend start,
Keycloak has the realm, roles and test users. The national **speed layer** works end to end on real data:
ODRÉ → poller → Kafka → stream → TimescaleDB (`grid-intelligence`). The read API, the platform features and the
frontend pages are not built yet.

## Architecture (target)

- **Lambda architecture**: a speed layer (Kafka Streams on provisional real-time data) and a batch layer
  (daily resync of consolidated/definitive data) feeding one serving store.
- **Bounded contexts** (DDD) as modules of a modular monolith, not microservices.
- Kafka as the event backbone between contexts; PostgreSQL + TimescaleDB for time series and user data;
  Keycloak for authentication (OIDC, PKCE).

Architecture overview: [`docs/architecture.md`](docs/architecture.md). Decisions and their alternatives:
[`docs/adr/`](docs/adr/). How we work (branches, commits, definition of done): [`CONTRIBUTING.md`](CONTRIBUTING.md).

## Repository layout

| Path | What it is |
|---|---|
| `docker-compose.yml` | The whole stack: TimescaleDB, Kafka, Keycloak, and the two backends and the frontend (each has its own `Dockerfile`) |
| `grid-intelligence/` | Spring Boot app that ingests and processes grid data (poller, stream, batch). Port 8081, DB schema `grid` |
| `platform/` | Spring Boot app with the persona features, as a Spring Modulith modular monolith. Port 8080, DB schema `platform` |
| `frontend/` | Angular app, one area per persona. Port 4200 |
| `infra/keycloak/` | Exported Keycloak realm `2bgreen` (roles, client, test users), imported automatically on first start |

## Prerequisites

- JDK 21 and Maven (each Spring app also ships a Maven wrapper, `./mvnw`)
- Node 22 LTS and Angular CLI 21 (`npm install -g @angular/cli@21`)
- Docker with the Compose plugin

## Getting started

### Everything in one command (Docker)

```bash
docker compose up -d --build
```

The first build takes a few minutes (Maven and npm downloads); the next ones reuse the cache. Compose starts the
services in order, each one waiting for the health of the ones it needs: TimescaleDB, Kafka and Keycloak, then
`grid-intelligence` (speed layer: poller and stream, and the read API), then `platform`, then the frontend. When
`docker compose ps` shows everything `healthy` (about 1 to 2 minutes), open http://localhost:4200 and log in as
`citizen` / `citizen` (see "Keycloak" below for the other users).

- **Wait for the first data.** On an empty database the poller backfills `eco2mix-national-tr` (about 70 ODRÉ calls) and the
  regional dataset (about 820 calls, once, about 1.7 % of the monthly quota). The national rows land within a minute or
  two, and the traffic light shows no reading (`NO_DATA`) until then. Reload the page after a minute.
- **Batch layer (optional).** `docker compose --profile batch up -d` also starts the daily resync of consolidated data. Its
  first run loads the whole history (about 257,000 rows, 17 calls).
- **Ports:** frontend 4200, `platform` 8080, `grid-intelligence` 8081 (internal API, no authentication, keep it off public
  networks), Keycloak 8180, Kafka 9092, TimescaleDB 5432. They must stay as they are: the browser calls `platform` and
  Keycloak on `localhost`, and the Keycloak client and the CORS rule expect the frontend on `http://localhost:4200`.
  So the stack is meant to run on the machine you browse from.
- **Keycloak and the token issuer.** Tokens are issued for `http://localhost:8180/realms/2bgreen` (the address the
  browser uses, fixed by `KC_HOSTNAME`). Inside Docker `platform` cannot reach that address, so it reads the signing keys
  at `http://keycloak:8080` (`jwk-set-uri`) and still checks `iss` against `localhost:8180`.
- **Data is kept** between `docker compose down` and the next `up`. `docker compose down -v` wipes it (the next start
  backfills again). Logs: `docker compose logs -f platform`.

The rest of this section is the mode for working on the code: only the infrastructure in Docker, the apps run by hand.

### 1. Infrastructure

```bash
docker compose up -d timescaledb kafka keycloak   # only the infrastructure, the apps run by hand below
docker compose ps        # timescaledb, kafka, keycloak should be "healthy"
```

| Service | Address | Credentials |
|---|---|---|
| TimescaleDB (PostgreSQL 17) | `localhost:5432`, database `2bgreen` | admin only: `postgres` / `postgres_dev`. Apps: `grid_app` / `grid_dev`, `platform_app` / `platform_dev` (see below) |
| Kafka (KRaft, single node) | `localhost:9092` | none |
| Keycloak | http://localhost:8180 | admin console: `admin` / `admin` |

Keycloak takes 20-40 seconds to start. On the first start it imports `infra/keycloak/2bgreen-realm.json`.

### 2. Backends

```bash
cd grid-intelligence && ./mvnw spring-boot:run     # http://localhost:8081
cd platform          && ./mvnw spring-boot:run     # http://localhost:8080
```

Each app connects with its own database role and Flyway runs its migrations in its own schema
(see "Database roles" below). Override the defaults with the `DB_USER` and `DB_PASSWORD` environment variables.
`curl http://localhost:8081/actuator/health` should return `{"status":"UP"}`.
`grid-intelligence` also serves its read API (contract K2, `docs/api/`) in every run mode:
`curl -i http://localhost:8081/api/v1/national/snapshots/latest` returns `404` (`NO_DATA`) until a snapshot is stored.
It is an internal API with no authentication: keep it on the Docker network, never expose it publicly.
On the platform, every endpoint requires a JWT, so `curl -i http://localhost:8080/actuator/health`
returning `401` is the expected result for now.

The platform reads grid data from the read API of `grid-intelligence` (`grid.client.mode=rest`, the default). To work
without `grid-intelligence`, Kafka and their data, serve the fixed K2 example instead. This data is fake and its date
never moves, so the app logs a warning at startup:

```bash
cd platform && ./mvnw spring-boot:run -Dspring-boot.run.arguments="--grid.client.mode=stub"
```

`GET /api/v1/advisory/traffic-light` (role `citizen`) returns the citizen's traffic light: `level` (`GREEN`, `AMBER`,
`RED` or `UNKNOWN`), `co2Intensity`, `observedAt` and `stale`. Errors are Problem Details with a stable `code`:
`404` `NO_DATA` (nothing stored yet), `503` `GRID_UNAVAILABLE` (grid data cannot be read).

| Setting (`advisory.traffic-light.*`) | Value | Meaning |
|---|---|---|
| `green-below` | `20` | Below this many gCO2eq/kWh, the light is green |
| `amber-below` | `40` | Below this, the light is amber; from this value, it is red |
| `max-age` | `PT2H` | A reading older than this is `stale`: no colour, level `UNKNOWN` |

The limits are a proposal, to validate as a team. They come from 6,234 national readings (2026-09-01 to 2026-11-04,
median 24, max 65) and split them into about 40% green, 35% amber and 25% red. That is autumn data only, so they are
to re-check in winter. The stub's reading is always `stale`; to see a colour with it, add
`--advisory.traffic-light.max-age=P3650D`.

Clean-grid alerts (U2). A citizen stores a threshold; the rule engine compares it with every national snapshot
published on `grid.snapshot-stored` and writes a notification when the CO2 intensity goes below it. The user is
always the `sub` of the token, never a value of the request.

| Endpoint | Role | What it does |
|---|---|---|
| `GET /api/v1/automation/alerts` | `citizen` | The caller's alerts: `id`, `threshold`, `active`, `createdAt`, `updatedAt` |
| `POST /api/v1/automation/alerts` | `citizen` | Body `{"threshold": 30}` (`active` optional, default `true`). `201` with the alert |
| `PUT /api/v1/automation/alerts/{id}` | `citizen` | Body `{"threshold": 30, "active": false}`, both required |
| `DELETE /api/v1/automation/alerts/{id}` | `citizen` | `204` |
| `GET /api/v1/notifications` | `citizen`, `regional_official` | `unreadCount` and `items` (the 50 newest: `id`, `type`, `message`, `createdAt`, `readAt`) |
| `POST /api/v1/notifications/{id}/read` | same | `204`, also when already read |
| `POST /api/v1/notifications/read-all` | same | `204` |
| `GET /api/v1/advisory/traffic-light/limits` | `citizen` | `greenBelow`, `amberBelow`: the alert form proposes them |

Errors are Problem Details with a `code`: `400` `INVALID_ALERT` (threshold outside 1 to 1000), `409`
`ALERT_LIMIT_REACHED` (5 alerts per user), `404` `ALERT_NOT_FOUND` / `NOTIFICATION_NOT_FOUND` (also for the alert or
notification of another user, so its existence is not revealed). Changing the threshold of an alert, pausing it or
switching it on again makes the engine forget what it knew about it: the next snapshot is compared as for a new alert.

To see one end to end, run the speed layer, `platform` and the frontend, log in as `citizen`, open "Mes alertes" and
create an alert at `1000`: the grid is always below, so the next snapshot stored (at most 15 minutes later) puts a
notification on the bell of the header, which asks `platform` once a minute. Then:

```bash
docker exec 2bgreen-timescaledb psql -U postgres -d 2bgreen -c \
  "SELECT id, user_id, threshold, active FROM platform.automation_alert_rule;
   SELECT subject_id, state, last_crossing_at, last_observed_at FROM platform.automation_rule_state;
   SELECT id, message, created_at, read_at FROM platform.delivery_notification ORDER BY id DESC LIMIT 5;"
```

API keys (U10). A DevOps user creates keys from the page "Mes clés d'API" (`/devops/cles`); scripts send one in the
`X-API-Key` header to call `/api/v1/public/**` (the carbon-intensity API, U7, below). Only the SHA-256 of a key is stored,
the key is shown once at creation (ADR-005: `docs/adr/0005-api-key-authentication.md`).

| Endpoint | Auth | What it does |
|---|---|---|
| `GET /api/v1/automation/api-keys` | JWT, `devops` | The caller's active keys: `id`, `name`, `prefix`, `createdAt`, `lastUsedAt`, `callsToday`, `dailyQuota` |
| `POST /api/v1/automation/api-keys` | JWT, `devops` | Body `{"name": "CI"}`. `201` with the same fields plus `key`, the only time it is given (`Cache-Control: no-store`) |
| `DELETE /api/v1/automation/api-keys/{id}` | JWT, `devops` | Revokes the key, `204`. The next call with it gets a `401` |
| `/api/v1/public/**` | `X-API-Key` | Separate security chain: no JWT, no CORS. Each accepted call answers `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset` (epoch seconds of the next 00:00 UTC) |

Errors: `400` `INVALID_API_KEY_REQUEST` (name of 1 to 60 characters), `409` `API_KEY_LIMIT_REACHED` (5 active keys
per user), `404` `API_KEY_NOT_FOUND` (also for another user's key). On the public paths: `401` `API_KEY_REQUIRED` (no
header) or `INVALID_API_KEY` (unknown or revoked, the same answer for both), `429` `QUOTA_EXCEEDED` with
`Retry-After` in seconds. The quota is `automation.api-keys.daily-quota` (1,000 calls per key and per UTC day); a
refused call is not counted. Check what is stored (the key itself appears nowhere):

```bash
docker exec 2bgreen-timescaledb psql -U postgres -d 2bgreen -c \
  "SELECT id, user_id, name, prefix, key_hash, revoked_at, last_used_at FROM platform.automation_api_key;
   SELECT * FROM platform.automation_api_key_daily_counter ORDER BY day DESC LIMIT 5;"
```

Carbon-intensity API (U7), for scripts and CI pipelines. Contract: `docs/api/public-openapi.yaml`, examples in
`docs/api/public-examples/`. `platform` reads the data from `grid-intelligence` (K2), which stays internal.

| Endpoint | What it returns |
|---|---|
| `GET /api/v1/public/intensity/latest[?scope=]` | Latest reading: `co2Intensity` (gCO2eq/kWh, `null` if unknown), `observedAt`, `quality`, `estimated`, `trafficLight` (same colour as the citizen light), `stale` (older than 2 h), `storedAt` |
| `GET /api/v1/public/intensity?from=&to=[&scope=]` | `items` with `observedAt`, `co2Intensity`, `quality`, `estimated`, 30-min grain, `from` included, `to` excluded, **7 days at most** |

`scope` is `national` (default) or `region:<INSEE code>` (`region:76`); a region's CO2 is an estimate from its
production mix (G7, `docs/data/regional-co2.md`), with `estimated: true`. Errors: `400 INVALID_PARAMETER`, `404 NO_DATA` (latest only),
`503 GRID_UNAVAILABLE`, plus the key and quota errors above. Every call that passes the key check counts against the
quota, `400` and `503` included. Try it with a key created on `/devops/cles`:

```bash
export TWOBGREEN_API_KEY=2bg_...
curl -s -H "X-API-Key: $TWOBGREEN_API_KEY" http://localhost:8080/api/v1/public/intensity/latest
curl -s -H "X-API-Key: $TWOBGREEN_API_KEY" \
  "http://localhost:8080/api/v1/public/intensity?from=2026-11-09T00:00:00Z&to=2026-11-10T00:00:00Z"
curl -si http://localhost:8080/api/v1/public/intensity/latest   # 401 API_KEY_REQUIRED
```

Regional dashboard (U11), the page of the regional official (`/elu`). The region is the `region_code` claim of the
token: a Keycloak user attribute that only an administrator can set (see "Keycloak" below). There is no parameter, so an
official sees their own region and no other.

| Endpoint | Role | What it returns |
|---|---|---|
| `GET /api/v1/regional/dashboard` | `regional_official` | `region` (`code`, `name`), `regional` (latest reading of the region), `national` (the nation in the same half hour, `null` if it has none), `maxAge` |

Each reading has `observedAt`, `storedAt`, `quality`, `consumption`, `production` (`nuclear`, `thermal`, `wind`,
`solar`, `hydro`, `bioenergy`, MW), `totalProduction`, `coverageRate` (production / consumption, %), `balance`
(`IMPORTING`, `EXPORTING`, `BALANCED`), `renewableShare` (%), `co2Intensity` and `co2Estimated`. `regional` also has
`stale`. A missing value is `null`, never 0, and so is every figure built on it.

- **The nation at the same time, not its latest reading.** Regional data comes hours later than national, so the latest
  national reading would describe another moment. `national` is the half hour of the regional reading, from the merged
  view (K2 range, 30-min average); the regional reading is a quarter-hour.
- **`thermal` is all fossil production.** Regional data has one aggregate; for the nation it is gas + coal + oil, so
  both columns show the same sources.
- **The total needs all six sources.** With one missing, `totalProduction`, `coverageRate`, `balance` and
  `renewableShare` are `null`: a total without it would be too small. Pumping and batteries are not production (as
  in G7).
- **Renewable share** = wind + solar + hydro + bioenergy over production. Regional hydro includes the turbines of
  pumped storage, which the data cannot separate.
- **Freshness has its own limit**, `regional.dashboard.max-age` (`PT4H`): ODRÉ publishes regional data 1 to 2 hours
  late and the regional poll runs hourly, so 3 hours behind is normal. Older, the reading is still shown, with
  `stale: true` and its time.
- **The regional CO2 is always an estimate** (G7, production-based: it ignores imports). The page labels it "estimée"
  and links to `docs/data/regional-co2.md`.

Errors are Problem Details with a `code`: `403` `REGION_NOT_ASSIGNED` (the token has no `region_code`, or one that
is not among the 12 regions), `404` `NO_DATA` (no reading for the region yet), `503` `GRID_UNAVAILABLE`. Another role
gets a plain `403`.

`grid-intelligence` does nothing by itself: its jobs are Spring profiles. To run the speed layer
(poll ODRÉ every 15 min, publish on Kafka, store in `speed_snapshot`):

```bash
cd grid-intelligence && ./mvnw spring-boot:run -Dspring-boot.run.profiles=poller,stream
```

On an empty database the first poll backfills everything `eco2mix-national-tr` holds (since 2026-09-01, about
50 ODRÉ calls, once). The regional dataset (`eco2mix-regional-tr`, 12 regions) is polled hourly by the same
profile and backfilled the same way (about 600 calls, once); its snapshots go on the same topic with the key
`region:<INSEE code>`. Settings: `src/main/resources/application-poller.properties`. Kafka clients log their whole
configuration at startup; to keep only the app's own lines, add
`-Dspring-boot.run.arguments="--logging.level.org.apache.kafka=WARN"`. How to check the result: see
"Checking the speed layer" below.

The batch layer (daily resync of `eco2mix-national-cons-def` and, since G6, `eco2mix-regional-cons-def` into
`batch_snapshot`) is its own profile and does not need the poller or the stream:

```bash
cd grid-intelligence && ./mvnw spring-boot:run -Dspring-boot.run.profiles=batch
```

On an empty table the first run loads the whole history (2012 to the last published date, about 257,000 rows, about
17 ODRÉ calls, once). Later runs cost one call and say `nothing changed` unless ODRÉ added rows or moved a year from
consolidated to definitive. Settings: `src/main/resources/application-batch.properties`.

The regional batch (G6) does the same for the 12 regions, from `batch.regional.first-year` (2021) on: about
210,000 rows per year, 7 calls for the first load, 1 call a day after. Each reading gets the regional CO2 estimate of
G7 (`estimated = true`), like the speed layer. **The first load is long:** ODRÉ sends a regional year at about 50 kB/s
(18.6 min for 2021 on 2026-11-11), so about 2 hours for 2021 to 2026, once. That is why exports have their own timeout,
`odre.export-read-timeout` (1 h); with the 20 s of the other calls every export failed, national included.
The `tco_*` / `tch_*` rates of the dataset are not loaded: no feature uses them (the dashboard of U11 computes its
coverage rate from consumption and production), and they would triple the download. Check the result:

```sql
SELECT scope, min(observed_at), max(observed_at), count(*) FROM grid.batch_snapshot GROUP BY scope ORDER BY scope;
-- one state per dataset: scope 'national' (G3) and 'regional' (G6, counts of all regions together)
SELECT scope, year, quality, row_count FROM grid.batch_sync_state ORDER BY scope, year, quality;
```

### 3. Frontend

```bash
cd frontend
npm install        # if it fails with "Cannot read properties of null (reading 'edgesOut')": npx npm@11 install
npm start          # http://localhost:4200
```

## Database roles

One role per bounded context, so the database itself enforces that `platform` never reads schema `grid`
(and the other way round). The script `infra/postgres/init/01-roles-and-schemas.sh` creates, on the first start
of the TimescaleDB container:

| Role | Password (dev) | Owns schema |
|---|---|---|
| `grid_app` | `grid_dev` | `grid` |
| `platform_app` | `platform_dev` | `platform` |

- Each role has no access at all to the other schema, and cannot create objects in `public`
  (it only has `USAGE` there, for the TimescaleDB functions such as `time_bucket`).
- The apps must not use `postgres`, which is for administration only.
- Passwords can be changed through `GRID_APP_PASSWORD` and `PLATFORM_APP_PASSWORD` (see `.env.example`).
- **Init scripts only run on an empty data volume.** After changing the script, recreate the volume:
  `docker compose rm -sf timescaledb && docker volume rm 2bgreen_timescale-data && docker compose up -d`.
- Same fix if `psql` fails with `could not access file "$libdir/timescaledb-<version>"`: the volume was created
  by another TimescaleDB image than the pinned one in `docker-compose.yml`.

## Keycloak: realm `2bgreen`

| Username | Password | Realm role | `region_code` |
|---|---|---|---|
| `citizen` | `citizen` | `citizen` | |
| `devops` | `devops` | `devops` | |
| `official` | `official` | `regional_official` | `93` (Provence-Alpes-Côte d'Azur) |

- Angular client: `2bgreen-frontend` (public client, authorization code flow with PKCE,
  redirect URI `http://localhost:4200/*`).
- **Region of an official (U11):** the user attribute `region_code` is declared in the realm's user profile, with
  the 12 INSEE codes of `eco2mix-regional-tr` as the only allowed values. Users can see it, only an administrator can
  change it: the account page refuses an edit (`error-user-attribute-read-only`). The client mapper `region_code` puts
  it in the access token. To attach another official: admin console > Users > the user > Attributes.
- The platform is a resource server: it validates tokens issued by
  `http://localhost:8180/realms/2bgreen` and checks the realm roles.
- Account page to test a login: http://localhost:8180/realms/2bgreen/account

**Changing the realm**: edit it in the admin console, then re-export it and commit the file:

```bash
docker compose stop keycloak
docker compose run --rm --no-deps -v "$(pwd)/infra/keycloak:/export" \
  keycloak export --realm 2bgreen --dir /export --users realm_file
docker compose up -d keycloak
```

## Useful commands

```bash
docker compose logs -f keycloak          # follow one service's logs
docker compose stop                      # stop everything, keep data
docker compose down -v                   # stop and WIPE all data (database, Kafka, Keycloak),
                                         # the realm is re-imported from infra/keycloak on next start
```

List Kafka topics:

```bash
docker exec 2bgreen-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
```

Open a SQL shell (as admin, or as an app role to see what that app can see):

```bash
docker exec -it 2bgreen-timescaledb psql -U postgres -d 2bgreen
docker exec -it -e PGPASSWORD=grid_dev 2bgreen-timescaledb psql -h localhost -U grid_app -d 2bgreen
```

### Checking the speed layer

With `poller,stream` running (see "Backends"). These read real data, they cost no ODRÉ call.

```bash
alias gsql='docker exec 2bgreen-timescaledb psql -U postgres -d 2bgreen -c'

# Rows stored: max should be ~30-60 min behind now (ODRÉ's publication lag), never in the future
gsql "SELECT count(*), min(observed_at), max(observed_at) FROM grid.speed_snapshot WHERE scope='national'"

# No forecast row became a snapshot: expect 0
gsql "SELECT count(*) FROM grid.speed_snapshot WHERE consumption IS NULL"

# Gaps in the 15-min series. Expected: only 2026-10-25 01:00 (missing at the source, see docs/data/eco2mix-national-tr.md)
gsql "SELECT observed_at, gap FROM (SELECT observed_at, observed_at - lag(observed_at) OVER (ORDER BY observed_at) AS gap
      FROM grid.speed_snapshot WHERE scope='national') g WHERE gap <> interval '15 minutes'"

# Regional readings (12 regions, hourly poll). Expect 12 scopes; CO2 is estimated (G7, docs/data/regional-co2.md),
# so with_co2 = estimated_rows, and both equal count(*) unless a production source was missing.
# Rows stored before G7 have no CO2: that doc says how to load them again.
gsql "SELECT scope, count(*), max(observed_at), count(co2_intensity) AS with_co2, count(*) FILTER (WHERE estimated)
      AS estimated_rows, round(avg(co2_intensity)) AS avg_co2 FROM grid.speed_snapshot
      WHERE scope LIKE 'region:%' GROUP BY scope ORDER BY scope"

# Messages: key = scope, K1 JSON; the stored topic adds storedAt
K=/opt/kafka/bin
docker exec 2bgreen-kafka $K/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic grid.snapshot-received --from-beginning --property print.key=true --max-messages 2
docker exec 2bgreen-kafka $K/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic grid.snapshot-stored --from-beginning --property print.key=true --max-messages 2

# Band changes (G9): one event per scope at the start, then only when the colour changes (not one per snapshot)
docker exec 2bgreen-kafka $K/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic grid.band-changed --from-beginning --property print.key=true

# Messages per partition: each poll adds ~4-5 (the 1 h overlap, absorbed by the upsert), not thousands
docker exec 2bgreen-kafka $K/kafka-get-offsets.sh --bootstrap-server localhost:9092 --topic grid.snapshot-received
```

To see a failure handled, run the app with `--odre.base-url=http://localhost:9` (ODRÉ unreachable) or
`docker stop 2bgreen-kafka` while it runs: each tick logs one warning or error, the app stays `UP`, and once the
dependency is back the next poll resumes from the same `after` (nothing skipped).

## Data sources

ODRÉ (Opendatasoft Explore API v2.1), RTE éCO2mix datasets:
`eco2mix-national-tr`, `eco2mix-regional-tr` (provisional, near real time),
`eco2mix-national-cons-def`, `eco2mix-regional-cons-def` (consolidated and definitive).
The API quota is 50,000 calls per user per month, so tests must never call the live API.

## Security note

All passwords in this repository are for local development only.
