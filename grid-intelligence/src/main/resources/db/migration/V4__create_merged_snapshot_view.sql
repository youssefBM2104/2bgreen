-- Merged read view (G4): the one series the read side queries, at 30-min grain.
-- For a period, the batch row wins (it is consolidated or definitive). Without one, the provisional quarter-hours of
-- that half hour are combined into one row. Quality is derived: PROVISIONAL for rows that come from speed_snapshot,
-- the batch row's own quality otherwise. A plain view, not materialized: always current, nothing to refresh.
--
-- Combination rule for the provisional branch: the AVERAGE of the quarter-hours in the half hour. Not yet verified
-- against cons-def (no overlap between tr and cons-def existed on 2026-11-10), see docs/data/merged-view.md.
-- It lives only in the second branch below, so changing the rule touches nothing else.
CREATE VIEW merged_snapshot AS
SELECT scope, observed_at, quality, co2_intensity, consumption,
       consumption_forecast_day, consumption_forecast_day_before,
       production_nuclear, production_gas, production_coal, production_oil, production_thermal,
       production_wind, production_solar, production_hydro, production_bioenergy,
       pumping, estimated, stored_at
FROM batch_snapshot
UNION ALL
SELECT s.scope,
       s.bucket AS observed_at,
       'PROVISIONAL'::text AS quality,
       avg(s.co2_intensity) AS co2_intensity,
       round(avg(s.consumption))::integer AS consumption,
       round(avg(s.consumption_forecast_day))::integer AS consumption_forecast_day,
       round(avg(s.consumption_forecast_day_before))::integer AS consumption_forecast_day_before,
       round(avg(s.production_nuclear))::integer AS production_nuclear,
       round(avg(s.production_gas))::integer AS production_gas,
       round(avg(s.production_coal))::integer AS production_coal,
       round(avg(s.production_oil))::integer AS production_oil,
       round(avg(s.production_thermal))::integer AS production_thermal,
       round(avg(s.production_wind))::integer AS production_wind,
       round(avg(s.production_solar))::integer AS production_solar,
       round(avg(s.production_hydro))::integer AS production_hydro,
       round(avg(s.production_bioenergy))::integer AS production_bioenergy,
       round(avg(s.pumping))::integer AS pumping,
       bool_or(s.estimated) AS estimated,
       max(s.stored_at) AS stored_at
FROM (
    -- "with measures" is the K1 test: a reading without consumption is not a reading
    SELECT time_bucket('30 minutes', observed_at) AS bucket, *
    FROM speed_snapshot
    WHERE consumption IS NOT NULL
) s
WHERE NOT EXISTS (
    SELECT 1 FROM batch_snapshot b WHERE b.scope = s.scope AND b.observed_at = s.bucket
)
GROUP BY s.scope, s.bucket;

COMMENT ON VIEW merged_snapshot IS 'Read side of the Lambda architecture: 30-min grain, batch row wins over provisional for the same period, provisional quarter-hours averaged.';
COMMENT ON COLUMN merged_snapshot.observed_at IS 'UTC start of the 30-min period.';
COMMENT ON COLUMN merged_snapshot.quality IS 'PROVISIONAL (from speed_snapshot), CONSOLIDATED or DEFINITIVE (from batch_snapshot).';
COMMENT ON COLUMN merged_snapshot.stored_at IS 'Last write of the row. For a provisional row, the latest of the quarter-hours it combines.';
