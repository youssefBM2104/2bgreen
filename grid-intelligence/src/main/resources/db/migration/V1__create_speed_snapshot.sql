-- Speed layer: provisional readings at 15-min grain. Single writer: the stream (D7).
-- Columns follow the K1 payload (docs/contracts/events.md). Every measure is nullable: missing is a valid state.
-- There is no quality column: anything in this table is PROVISIONAL, the read view derives it.
CREATE TABLE speed_snapshot (
    scope                           text           NOT NULL,
    observed_at                     timestamptz    NOT NULL,
    co2_intensity                   numeric,
    consumption                     integer,
    consumption_forecast_day        integer,
    consumption_forecast_day_before integer,
    production_nuclear              integer,
    production_gas                  integer,
    production_coal                 integer,
    production_oil                  integer,
    production_thermal              integer,
    production_wind                 integer,
    production_solar                integer,
    production_hydro                integer,
    production_bioenergy            integer,
    pumping                         integer,
    estimated                       boolean        NOT NULL DEFAULT false,
    stored_at                       timestamptz    NOT NULL DEFAULT now(),
    -- idempotency key: INSERT ... ON CONFLICT (scope, observed_at) DO UPDATE
    PRIMARY KEY (scope, observed_at)
);

SELECT create_hypertable('speed_snapshot', 'observed_at');

COMMENT ON TABLE speed_snapshot IS 'Speed layer: provisional readings, 15-min grain, written only by the stream.';
COMMENT ON COLUMN speed_snapshot.scope IS '''national'' or ''region:<code>''.';
COMMENT ON COLUMN speed_snapshot.observed_at IS 'UTC start of the 15-min period.';
COMMENT ON COLUMN speed_snapshot.co2_intensity IS 'gCO2eq/kWh. Estimated for regions (see estimated).';
COMMENT ON COLUMN speed_snapshot.consumption IS 'MW.';
COMMENT ON COLUMN speed_snapshot.consumption_forecast_day IS 'MW. Consumption forecast (not CO2).';
COMMENT ON COLUMN speed_snapshot.consumption_forecast_day_before IS 'MW. Consumption forecast issued the day before (not CO2).';
COMMENT ON COLUMN speed_snapshot.production_gas IS 'MW. National only.';
COMMENT ON COLUMN speed_snapshot.production_coal IS 'MW. National only.';
COMMENT ON COLUMN speed_snapshot.production_oil IS 'MW. National only.';
COMMENT ON COLUMN speed_snapshot.production_thermal IS 'MW. Fossil aggregate, set when the source has no gas/coal/oil split (regional).';
COMMENT ON COLUMN speed_snapshot.production_wind IS 'MW. Total, onshore + offshore.';
COMMENT ON COLUMN speed_snapshot.production_hydro IS 'MW. Total, turbine output included.';
COMMENT ON COLUMN speed_snapshot.pumping IS 'MW drawn from the grid to pump water. Zero or negative.';
COMMENT ON COLUMN speed_snapshot.estimated IS 'True when CO2 is estimated (regional).';
COMMENT ON COLUMN speed_snapshot.stored_at IS 'UTC time the row was saved (storedAt on grid.snapshot-stored).';
