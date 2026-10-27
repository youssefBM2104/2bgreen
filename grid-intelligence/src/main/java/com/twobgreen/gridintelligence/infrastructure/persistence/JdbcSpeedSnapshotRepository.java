package com.twobgreen.gridintelligence.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.twobgreen.gridintelligence.application.SpeedSnapshotRepository;
import com.twobgreen.gridintelligence.domain.GridSnapshot;
import com.twobgreen.gridintelligence.domain.Production;

@Repository
class JdbcSpeedSnapshotRepository implements SpeedSnapshotRepository {

	// Replaces the whole row on conflict: the latest message is the truth, a field that is now missing becomes NULL
	private static final String UPSERT = """
			INSERT INTO speed_snapshot (
			    scope, observed_at, co2_intensity, consumption,
			    consumption_forecast_day, consumption_forecast_day_before,
			    production_nuclear, production_gas, production_coal, production_oil, production_thermal,
			    production_wind, production_solar, production_hydro, production_bioenergy,
			    pumping, estimated)
			VALUES (
			    :scope, :observedAt, :co2Intensity, :consumption,
			    :forecastDay, :forecastDayBefore,
			    :nuclear, :gas, :coal, :oil, :thermal,
			    :wind, :solar, :hydro, :bioenergy,
			    :pumping, :estimated)
			ON CONFLICT (scope, observed_at) DO UPDATE SET
			    co2_intensity = EXCLUDED.co2_intensity,
			    consumption = EXCLUDED.consumption,
			    consumption_forecast_day = EXCLUDED.consumption_forecast_day,
			    consumption_forecast_day_before = EXCLUDED.consumption_forecast_day_before,
			    production_nuclear = EXCLUDED.production_nuclear,
			    production_gas = EXCLUDED.production_gas,
			    production_coal = EXCLUDED.production_coal,
			    production_oil = EXCLUDED.production_oil,
			    production_thermal = EXCLUDED.production_thermal,
			    production_wind = EXCLUDED.production_wind,
			    production_solar = EXCLUDED.production_solar,
			    production_hydro = EXCLUDED.production_hydro,
			    production_bioenergy = EXCLUDED.production_bioenergy,
			    pumping = EXCLUDED.pumping,
			    estimated = EXCLUDED.estimated,
			    stored_at = now()
			RETURNING stored_at
			""";

	// Zero or one row; walks the hypertable's (observed_at DESC) index instead of aggregating
	private static final String LATEST = """
			SELECT observed_at FROM speed_snapshot WHERE scope = :scope ORDER BY observed_at DESC LIMIT 1
			""";

	private final JdbcClient jdbc;

	JdbcSpeedSnapshotRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Instant upsert(GridSnapshot snapshot) {
		Production production = snapshot.production();
		Timestamp storedAt = jdbc.sql(UPSERT)
				.param("scope", snapshot.scope())
				.param("observedAt", Timestamp.from(snapshot.observedAt()))
				.param("co2Intensity", snapshot.co2Intensity())
				.param("consumption", snapshot.consumption())
				.param("forecastDay", snapshot.consumptionForecastDay())
				.param("forecastDayBefore", snapshot.consumptionForecastDayBefore())
				.param("nuclear", production.nuclear())
				.param("gas", production.gas())
				.param("coal", production.coal())
				.param("oil", production.oil())
				.param("thermal", production.thermal())
				.param("wind", production.wind())
				.param("solar", production.solar())
				.param("hydro", production.hydro())
				.param("bioenergy", production.bioenergy())
				.param("pumping", snapshot.pumping())
				.param("estimated", snapshot.estimated())
				.query(Timestamp.class)
				.single();
		return storedAt.toInstant();
	}

	@Override
	public Optional<Instant> latestObservedAt(String scope) {
		return jdbc.sql(LATEST)
				.param("scope", scope)
				.query(Timestamp.class)
				.optional()
				.map(Timestamp::toInstant);
	}
}
