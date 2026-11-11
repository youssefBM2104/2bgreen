package com.twobgreen.gridintelligence.infrastructure.persistence;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.twobgreen.gridintelligence.application.SnapshotReader;
import com.twobgreen.gridintelligence.application.StoredSnapshot;
import com.twobgreen.gridintelligence.domain.GridSnapshot;
import com.twobgreen.gridintelligence.domain.Production;
import com.twobgreen.gridintelligence.domain.Quality;

/**
 * Reads for the read API. {@code latest} reads the speed layer table, where everything is provisional and there is no
 * quality column. Ranges read the merged view {@code merged_snapshot} (G4), which carries the quality.
 */
@Repository
class JdbcSnapshotReader implements SnapshotReader {

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

	// The view already holds one row per (scope, 30-min period), batch first. Missing periods stay missing.
	private static final String RANGE = """
			SELECT scope, observed_at, quality, co2_intensity, consumption,
			       consumption_forecast_day, consumption_forecast_day_before,
			       production_nuclear, production_gas, production_coal, production_oil, production_thermal,
			       production_wind, production_solar, production_hydro, production_bioenergy,
			       pumping, estimated, stored_at
			FROM merged_snapshot
			WHERE scope = :scope AND observed_at >= :from AND observed_at < :to
			ORDER BY observed_at
			""";

	private final JdbcClient jdbc;

	JdbcSnapshotReader(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<StoredSnapshot> latestWithMeasures(String scope) {
		return jdbc.sql(LATEST_WITH_MEASURES)
				.param("scope", scope)
				.query((rs, rowNumber) -> toStoredSnapshot(rs, Quality.PROVISIONAL))
				.optional();
	}

	@Override
	public List<StoredSnapshot> range(String scope, Instant from, Instant to) {
		return jdbc.sql(RANGE)
				.param("scope", scope)
				.param("from", Timestamp.from(from))
				.param("to", Timestamp.from(to))
				.query((rs, rowNumber) -> toStoredSnapshot(rs, Quality.valueOf(rs.getString("quality"))))
				.list();
	}

	private static StoredSnapshot toStoredSnapshot(ResultSet rs, Quality quality) throws SQLException {
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
				plain(rs.getBigDecimal("co2_intensity")),
				integer(rs, "consumption"),
				integer(rs, "consumption_forecast_day"),
				integer(rs, "consumption_forecast_day_before"),
				production,
				integer(rs, "pumping"),
				quality,
				rs.getBoolean("estimated"));
		return new StoredSnapshot(snapshot, rs.getTimestamp("stored_at").toInstant());
	}

	// The view averages the quarter-hours of a provisional period: avg() gives 11.0000000000000000 for two readings of
	// 11. A figure is written without the zeros that carry no information, and never in scientific notation.
	private static BigDecimal plain(BigDecimal value) {
		if (value == null) {
			return null;
		}
		BigDecimal stripped = value.stripTrailingZeros();
		return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
	}

	// getInt would turn a SQL NULL into 0: a missing value must stay missing
	private static Integer integer(ResultSet rs, String column) throws SQLException {
		return rs.getObject(column, Integer.class);
	}
}
