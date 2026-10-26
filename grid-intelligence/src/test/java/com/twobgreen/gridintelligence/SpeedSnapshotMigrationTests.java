package com.twobgreen.gridintelligence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SpeedSnapshotMigrationTests {

	// Values of the committed fixture national-tr-measured.json
	private static final String UPSERT = """
			INSERT INTO speed_snapshot (scope, observed_at, co2_intensity, consumption, production_nuclear)
			VALUES ('national', '2026-10-20T14:45:00Z', ?, 49970, 38499)
			ON CONFLICT (scope, observed_at) DO UPDATE
			SET co2_intensity = EXCLUDED.co2_intensity, consumption = EXCLUDED.consumption,
			    production_nuclear = EXCLUDED.production_nuclear
			""";

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void migrationAppliedAsGridApp() {
		assertThat(jdbc.queryForObject("SELECT current_user", String.class)).isEqualTo("grid_app");
		assertThat(jdbc.queryForObject(
				"SELECT success FROM grid.flyway_schema_history WHERE version = '1'", Boolean.class)).isTrue();
		assertThat(jdbc.queryForObject(
				"SELECT tableowner FROM pg_tables WHERE schemaname = 'grid' AND tablename = 'speed_snapshot'",
				String.class)).isEqualTo("grid_app");
	}

	@Test
	void tableIsAHypertableOnObservedAt() {
		assertThat(jdbc.queryForObject("""
				SELECT d.column_name
				FROM timescaledb_information.hypertables h
				JOIN timescaledb_information.dimensions d USING (hypertable_schema, hypertable_name)
				WHERE h.hypertable_schema = 'grid' AND h.hypertable_name = 'speed_snapshot'
				""", String.class)).isEqualTo("observed_at");
	}

	@Test
	void insertingTheSameRowTwiceKeepsOneRowAndTheLatestValues() {
		jdbc.update("DELETE FROM speed_snapshot");

		jdbc.update(UPSERT, 29);
		jdbc.update(UPSERT, 31);

		assertThat(jdbc.queryForObject("SELECT count(*) FROM speed_snapshot", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT co2_intensity FROM speed_snapshot", Integer.class)).isEqualTo(31);
	}

	@Test
	void onlyTheKeyIsRequired() {
		jdbc.update("DELETE FROM speed_snapshot");

		jdbc.update("INSERT INTO speed_snapshot (scope, observed_at) VALUES ('region:76', '2026-10-20T14:45:00Z')");

		assertThat(jdbc.queryForObject(
				"SELECT consumption IS NULL AND co2_intensity IS NULL AND estimated = false FROM speed_snapshot",
				Boolean.class)).isTrue();
	}

}
