package com.twobgreen.gridintelligence.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.twobgreen.gridintelligence.application.SnapshotReader;
import com.twobgreen.gridintelligence.application.StoredSnapshot;
import com.twobgreen.gridintelligence.domain.GridSnapshot;
import com.twobgreen.gridintelligence.domain.Production;
import com.twobgreen.gridintelligence.domain.Quality;

/**
 * Reads the speed layer table for the read API. Everything in {@code speed_snapshot} is provisional, there is no
 * quality column.
 */
@Repository
class JdbcSpeedSnapshotReader implements SnapshotReader {

	// "With measures" is the K1 test: a measured consumption. Zero or one row, walks the primary key (scope,
	// observed_at) backwards instead of sorting.
	private static final String LATEST_WITH_MEASURES = """
			SELECT scope, observed_at, co2_intensity, consumption,
			       consumption_forecast_day, consumption_forecast_day_before,
			       production_nuclear, production_gas, production_coal, production_oil, production_thermal,
			       production_wind, production_solar, production_hydro, production_bioenergy,
			       pumping, estimated, stored_at
			FROM speed_snapshot
			WHERE scope = :scope AND consumption IS NOT NULL
			ORDER BY observed_at DESC
			LIMIT 1
			""";

	private final JdbcClient jdbc;

	JdbcSpeedSnapshotReader(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<StoredSnapshot> latestWithMeasures(String scope) {
		return jdbc.sql(LATEST_WITH_MEASURES)
				.param("scope", scope)
				.query(JdbcSpeedSnapshotReader::toStoredSnapshot)
				.optional();
	}

	private static StoredSnapshot toStoredSnapshot(ResultSet rs, int rowNumber) throws SQLException {
		Production production = new Production(
				integer(rs, "production_nuclear"),
				integer(rs, "production_gas"),
				integer(rs, "production_coal"),
				integer(rs, "production_oil"),
				integer(rs, "production_thermal"),
				integer(rs, "production_wind"),
				integer(rs, "production_solar"),
				integer(rs, "production_hydro"),
				integer(rs, "production_bioenergy"));
		GridSnapshot snapshot = new GridSnapshot(
				rs.getString("scope"),
				rs.getTimestamp("observed_at").toInstant(),
				rs.getBigDecimal("co2_intensity"),
				integer(rs, "consumption"),
				integer(rs, "consumption_forecast_day"),
				integer(rs, "consumption_forecast_day_before"),
				production,
				integer(rs, "pumping"),
				Quality.PROVISIONAL,
				rs.getBoolean("estimated"));
		return new StoredSnapshot(snapshot, rs.getTimestamp("stored_at").toInstant());
	}

	// getInt would turn a SQL NULL into 0: a missing value must stay missing
	private static Integer integer(ResultSet rs, String column) throws SQLException {
		return rs.getObject(column, Integer.class);
	}
}
