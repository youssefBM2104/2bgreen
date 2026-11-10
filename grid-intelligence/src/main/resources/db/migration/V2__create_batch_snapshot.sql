-- Batch layer: consolidated and definitive readings at 30-min grain. Single writer: the batch (G3).
-- Same business columns as speed_snapshot (K1 payload) plus quality. Rebuildable from eco2mix-*-cons-def at any time,
-- so it holds no data that cannot be fetched again. Every measure is nullable: missing is a valid state.
-- The merged read view (G4) picks this row over the provisional one for the same period.
CREATE TABLE batch_snapshot (
    scope                           text           NOT NULL,
    observed_at                     timestamptz    NOT NULL,
    -- from ODRE `nature`: rows change quality in place (consolidated -> definitive), the batch upserts them
    quality                         text           NOT NULL,
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
    PRIMARY KEY (scope, observed_at),
    -- PROVISIONAL only exists in speed_snapshot: it is never a batch quality
    CONSTRAINT batch_snapshot_quality_check CHECK (quality IN ('CONSOLIDATED', 'DEFINITIVE'))
);

SELECT create_hypertable('batch_snapshot', 'observed_at');

COMMENT ON TABLE batch_snapshot IS 'Batch layer: consolidated/definitive readings, 30-min grain, written only by the batch.';
COMMENT ON COLUMN batch_snapshot.scope IS '''national'' or ''region:<code>''.';
COMMENT ON COLUMN batch_snapshot.observed_at IS 'UTC start of the 30-min period.';
COMMENT ON COLUMN batch_snapshot.quality IS 'CONSOLIDATED or DEFINITIVE, from ODRE nature. PROVISIONAL is refused.';
COMMENT ON COLUMN batch_snapshot.co2_intensity IS 'gCO2eq/kWh. Estimated for regions (see estimated).';
COMMENT ON COLUMN batch_snapshot.consumption IS 'MW.';
COMMENT ON COLUMN batch_snapshot.consumption_forecast_day IS 'MW. Consumption forecast (not CO2).';
COMMENT ON COLUMN batch_snapshot.consumption_forecast_day_before IS 'MW. Consumption forecast issued the day before (not CO2).';
COMMENT ON COLUMN batch_snapshot.production_gas IS 'MW. National only.';
COMMENT ON COLUMN batch_snapshot.production_coal IS 'MW. National only.';
COMMENT ON COLUMN batch_snapshot.production_oil IS 'MW. National only.';
COMMENT ON COLUMN batch_snapshot.production_thermal IS 'MW. Fossil aggregate, set when the source has no gas/coal/oil split (regional).';
COMMENT ON COLUMN batch_snapshot.production_wind IS 'MW. Total (cons-def has no onshore/offshore split).';
COMMENT ON COLUMN batch_snapshot.production_hydro IS 'MW. Total, turbine output included.';
COMMENT ON COLUMN batch_snapshot.pumping IS 'MW drawn from the grid to pump water. Zero or negative.';
COMMENT ON COLUMN batch_snapshot.estimated IS 'True when CO2 is estimated (regional).';
COMMENT ON COLUMN batch_snapshot.stored_at IS 'UTC time the row was last written (a quality upgrade rewrites it).';
