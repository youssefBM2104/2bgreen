#!/bin/bash
set -euo pipefail

psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  -v grid_pw="$GRID_APP_PASSWORD" -v platform_pw="$PLATFORM_APP_PASSWORD" <<'SQL'
CREATE EXTENSION IF NOT EXISTS timescaledb;

CREATE ROLE grid_app     LOGIN PASSWORD :'grid_pw';
CREATE ROLE platform_app LOGIN PASSWORD :'platform_pw';

CREATE SCHEMA grid     AUTHORIZATION grid_app;
CREATE SCHEMA platform AUTHORIZATION platform_app;

-- public stays on the path (read-only) because the timescaledb functions live there
ALTER ROLE grid_app     SET search_path = grid, public;
ALTER ROLE platform_app SET search_path = platform, public;

REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT  USAGE ON SCHEMA public TO grid_app, platform_app;
REVOKE CONNECT ON DATABASE "2bgreen" FROM PUBLIC;
GRANT  CONNECT ON DATABASE "2bgreen" TO grid_app, platform_app;
SQL
