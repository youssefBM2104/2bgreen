package com.twobgreen.gridintelligence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** V2 (batch_snapshot): the table the batch layer writes, same keys and columns as the speed table plus quality. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BatchSnapshotMigrationTests {

	// Values of the committed fixture national-consdef-definitive-measured.json would do; only the shape matters here
	private static final String UPSERT = """
			INSERT INTO batch_snapshot (scope, observed_at, quality, co2_intensity, consumption)
			VALUES ('national', '2025-06-01T10:00:00Z', ?, ?, 41000)
			ON CONFLICT (scope, observed_at) DO UPDATE
			SET quality = EXCLUDED.quality, co2_intensity = EXCLUDED.co2_intensity,
			    consumption = EXCLUDED.consumption
			""";

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void migrationAppliedAsGridApp() {
		assertThat(jdbc.queryForObject(
				"SELECT success FROM grid.flyway_schema_history WHERE version = '2'", Boolean.class)).isTrue();
		assertThat(jdbc.queryForObject(
				"SELECT tableowner FROM pg_tables WHERE schemaname = 'grid' AND tablename = 'batch_snapshot'",
				String.class)).isEqualTo("grid_app");
	}

	@Test
	void migrationPassesOnADatabaseWhereV1IsAlreadyApplied() {
		try (PostgreSQLContainer postgres = TestcontainersConfiguration.newPostgres()) {
			postgres.start();
			DriverManagerDataSource grid = new DriverManagerDataSource(postgres.getJdbcUrl(), "grid_app", "grid_dev");
			Flyway.configure().dataSource(grid).schemas("grid").defaultSchema("grid").createSchemas(false)
					.target("1").load().migrate();
			JdbcTemplate db = new JdbcTemplate(grid);
			db.update("INSERT INTO grid.speed_snapshot (scope, observed_at, consumption) "
					+ "VALUES ('national', '2026-10-20T14:45:00Z', 49970)");

			Flyway.configure().dataSource(grid).schemas("grid").defaultSchema("grid").createSchemas(false)
					.load().migrate();

			assertThat(db.queryForObject("SELECT count(*) FROM grid.batch_snapshot", Integer.class)).isZero();
			assertThat(db.queryForObject("SELECT consumption FROM grid.speed_snapshot", Integer.class))
					.isEqualTo(49970);
		}
	}

	@Test
	void tableIsAHypertableOnObservedAt() {
		assertThat(jdbc.queryForObject("""
				SELECT d.column_name
				FROM timescaledb_information.hypertables h
				JOIN timescaledb_information.dimensions d USING (hypertable_schema, hypertable_name)
				WHERE h.hypertable_schema = 'grid' AND h.hypertable_name = 'batch_snapshot'
				""", String.class)).isEqualTo("observed_at");
	}

	@Test
	void sameBusinessColumnsAsTheSpeedTablePlusQuality() {
		String columns = """
				SELECT column_name FROM information_schema.columns
				WHERE table_schema = 'grid' AND table_name = ? ORDER BY column_name
				""";
		var speed = jdbc.queryForList(columns, String.class, "speed_snapshot");
		var batch = jdbc.queryForList(columns, String.class, "batch_snapshot");

		assertThat(batch).containsExactlyInAnyOrderElementsOf(
				java.util.stream.Stream.concat(speed.stream(), java.util.stream.Stream.of("quality")).toList());
	}

	@Test
	void insertingTheSameRowTwiceKeepsOneRowAndTheLatestValues() {
		jdbc.update("DELETE FROM batch_snapshot");

		jdbc.update(UPSERT, "CONSOLIDATED", 29);
		jdbc.update(UPSERT, "CONSOLIDATED", 29);

		assertThat(jdbc.queryForObject("SELECT count(*) FROM batch_snapshot", Integer.class)).isEqualTo(1);
	}

	@Test
	void aQualityUpgradeRewritesTheRowInPlace() {
		jdbc.update("DELETE FROM batch_snapshot");

		jdbc.update(UPSERT, "CONSOLIDATED", 29);
		jdbc.update(UPSERT, "DEFINITIVE", 31);

		assertThat(jdbc.queryForObject("SELECT count(*) FROM batch_snapshot", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT quality FROM batch_snapshot", String.class)).isEqualTo("DEFINITIVE");
		assertThat(jdbc.queryForObject("SELECT co2_intensity FROM batch_snapshot", Integer.class)).isEqualTo(31);
	}

	@Test
	void qualityIsRequiredAndMustBeConsolidatedOrDefinitive() {
		assertThatThrownBy(() -> jdbc.update(
				"INSERT INTO batch_snapshot (scope, observed_at) VALUES ('national', '2025-06-01T10:30:00Z')"))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update(UPSERT, "PROVISIONAL", 29))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void otherMeasuresAreOptional() {
		jdbc.update("DELETE FROM batch_snapshot");

		jdbc.update("INSERT INTO batch_snapshot (scope, observed_at, quality) "
				+ "VALUES ('region:76', '2025-06-01T10:00:00Z', 'DEFINITIVE')");

		assertThat(jdbc.queryForObject(
				"SELECT consumption IS NULL AND co2_intensity IS NULL AND estimated = false FROM batch_snapshot",
				Boolean.class)).isTrue();
	}
}
