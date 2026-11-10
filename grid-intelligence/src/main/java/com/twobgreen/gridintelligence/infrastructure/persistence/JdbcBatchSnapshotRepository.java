package com.twobgreen.gridintelligence.infrastructure.persistence;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.twobgreen.gridintelligence.application.BatchSnapshotRepository;
import com.twobgreen.gridintelligence.domain.GridSnapshot;
import com.twobgreen.gridintelligence.domain.Production;

@Repository
class JdbcBatchSnapshotRepository implements BatchSnapshotRepository {

	// Statements per round trip: a year is about 17,500 rows
	private static final int BATCH_SIZE = 1000;

	// The WHERE clause is what makes a run idempotent and quality-safe. A row it rejects counts 0 updated rows:
	//  - never replace DEFINITIVE with CONSOLIDATED (a late or stale read must not downgrade a row)
	//  - never rewrite identical data, so a second run touches nothing, stored_at included
	private static final String UPSERT = """
			INSERT INTO batch_snapshot (
			    scope, observed_at, quality, co2_intensity, consumption,
			    consumption_forecast_day, consumption_forecast_day_before,
			    production_nuclear, production_gas, production_coal, production_oil, production_thermal,
			    production_wind, production_solar, production_hydro, production_bioenergy,
			    pumping, estimated)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			ON CONFLICT (scope, observed_at) DO UPDATE SET
			    quality = EXCLUDED.quality,
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
			WHERE NOT (batch_snapshot.quality = 'DEFINITIVE' AND EXCLUDED.quality = 'CONSOLIDATED')
			  AND (batch_snapshot.quality, batch_snapshot.co2_intensity, batch_snapshot.consumption,
			       batch_snapshot.consumption_forecast_day, batch_snapshot.consumption_forecast_day_before,
			       batch_snapshot.production_nuclear, batch_snapshot.production_gas, batch_snapshot.production_coal,
			       batch_snapshot.production_oil, batch_snapshot.production_thermal, batch_snapshot.production_wind,
			       batch_snapshot.production_solar, batch_snapshot.production_hydro,
			       batch_snapshot.production_bioenergy, batch_snapshot.pumping, batch_snapshot.estimated)
			      IS DISTINCT FROM
			      (EXCLUDED.quality, EXCLUDED.co2_intensity, EXCLUDED.consumption,
			       EXCLUDED.consumption_forecast_day, EXCLUDED.consumption_forecast_day_before,
			       EXCLUDED.production_nuclear, EXCLUDED.production_gas, EXCLUDED.production_coal,
			       EXCLUDED.production_oil, EXCLUDED.production_thermal, EXCLUDED.production_wind,
			       EXCLUDED.production_solar, EXCLUDED.production_hydro, EXCLUDED.production_bioenergy,
			       EXCLUDED.pumping, EXCLUDED.estimated)
			""";

	private final JdbcTemplate jdbc;

	JdbcBatchSnapshotRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public int upsert(List<GridSnapshot> snapshots) {
		int written = 0;
		// batchUpdate sends BATCH_SIZE statements per round trip and answers one array of counts per round trip
		for (int[] counts : jdbc.batchUpdate(UPSERT, snapshots, BATCH_SIZE, JdbcBatchSnapshotRepository::bind)) {
			for (int count : counts) {
				// SUCCESS_NO_INFO would make the "rows written" figure a guess: better to stop than to report it
				if (count < 0) {
					throw new IllegalStateException("the driver did not report the rows written by the batch");
				}
				written += count;
			}
		}
		return written;
	}

	private static void bind(PreparedStatement statement, GridSnapshot snapshot) throws SQLException {
		Production production = snapshot.production();
		int i = 1;
		statement.setString(i++, snapshot.scope());
		statement.setTimestamp(i++, Timestamp.from(snapshot.observedAt()));
		statement.setString(i++, snapshot.quality().name());
		statement.setObject(i++, snapshot.co2Intensity());
		statement.setObject(i++, snapshot.consumption());
		statement.setObject(i++, snapshot.consumptionForecastDay());
		statement.setObject(i++, snapshot.consumptionForecastDayBefore());
		statement.setObject(i++, production.nuclear());
		statement.setObject(i++, production.gas());
		statement.setObject(i++, production.coal());
		statement.setObject(i++, production.oil());
		statement.setObject(i++, production.thermal());
		statement.setObject(i++, production.wind());
		statement.setObject(i++, production.solar());
		statement.setObject(i++, production.hydro());
		statement.setObject(i++, production.bioenergy());
		statement.setObject(i++, snapshot.pumping());
		statement.setBoolean(i, snapshot.estimated());
	}
}
