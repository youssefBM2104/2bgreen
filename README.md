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

## Repository layout

| Path | What it is |
|---|---|
| `docker-compose.yml` | Local infrastructure: TimescaleDB, Kafka, Keycloak |
| `grid-intelligence/` | Spring Boot app that ingests and processes grid data (poller, stream, batch). Port 8081, DB schema `grid` |
| `platform/` | Spring Boot app with the persona features, as a Spring Modulith modular monolith. Port 8080, DB schema `platform` |
| `frontend/` | Angular app, one area per persona. Port 4200 |
| `infra/keycloak/` | Exported Keycloak realm `2bgreen` (roles, client, test users), imported automatically on first start |

## Prerequisites

- JDK 21 and Maven (each Spring app also ships a Maven wrapper, `./mvnw`)
- Node 22 LTS and Angular CLI 21 (`npm install -g @angular/cli@21`)
- Docker with the Compose plugin

## Getting started

### 1. Infrastructure

```bash
docker compose up -d
docker compose ps        # timescaledb, kafka, keycloak should be "Up"
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
On the platform, every endpoint requires a JWT, so `curl -i http://localhost:8080/actuator/health`
returning `401` is the expected result for now.

`grid-intelligence` does nothing by itself: its jobs are Spring profiles. To run the speed layer
(poll ODRÉ every 15 min, publish on Kafka, store in `speed_snapshot`):

```bash
cd grid-intelligence && ./mvnw spring-boot:run -Dspring-boot.run.profiles=poller,stream
```

On an empty database the first poll backfills everything `eco2mix-national-tr` holds (since 2026-09-01, about
50 ODRÉ calls, once). Settings: `src/main/resources/application-poller.properties`. Kafka clients log their whole
configuration at startup; to keep only the app's own lines, add
`-Dspring-boot.run.arguments="--logging.level.org.apache.kafka=WARN"`. How to check the result: see
"Checking the speed layer" below.

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

| Username | Password | Realm role |
|---|---|---|
| `citizen` | `citizen` | `citizen` |
| `devops` | `devops` | `devops` |
| `official` | `official` | `regional_official` |

- Angular client: `2bgreen-frontend` (public client, authorization code flow with PKCE,
  redirect URI `http://localhost:4200/*`).
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

# Messages: key = scope, K1 JSON; the stored topic adds storedAt
K=/opt/kafka/bin
docker exec 2bgreen-kafka $K/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic grid.snapshot-received --from-beginning --property print.key=true --max-messages 2
docker exec 2bgreen-kafka $K/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic grid.snapshot-stored --from-beginning --property print.key=true --max-messages 2

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
