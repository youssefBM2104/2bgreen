package com.twobgreen.gridintelligence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * G4: the merged view, on the real TimescaleDB. Rows go in through plain SQL (the two tables have their own writers
 * and tests), the view is read back. One test per acceptance criterion, then the edge cases of the combination.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class MergedSnapshotViewTests {

	private static final String NATIONAL = "national";

	@Autowired
	JdbcTemplate jdbc;

	@BeforeEach
	void emptyTables() {
		jdbc.update("DELETE FROM speed_snapshot");
		jdbc.update("DELETE FROM batch_snapshot");
	}

	// ---- acceptance criteria -------------------------------------------------------------------------------------

	@Test
	void aPeriodInBothTablesGivesTheBatchRowOnly() {
		provisional(NATIONAL, "2026-09-15T12:00:00Z", 49260, 11);
		provisional(NATIONAL, "2026-09-15T12:15:00Z", 50223, 11);
		batch(NATIONAL, "2026-09-15T12:00:00Z", "CONSOLIDATED", 49500, 12);

		List<Map<String, Object>> rows = view();

		assertThat(rows).hasSize(1);
		assertThat(rows.getFirst()).containsEntry("quality", "CONSOLIDATED").containsEntry("consumption", 49500);
		assertThat((BigDecimal) rows.getFirst().get("co2_intensity")).isEqualByComparingTo("12");
	}

	@Test
	void theQualityIsOneOfTheThreeValues() {
		batch(NATIONAL, "2025-12-31T23:30:00Z", "DEFINITIVE", 40000, 20);
		batch(NATIONAL, "2026-08-31T21:30:00Z", "CONSOLIDATED", 43979, 29);
		provisional(NATIONAL, "2026-09-15T12:00:00Z", 49260, 11);

		assertThat(jdbc.queryForList("SELECT DISTINCT quality FROM merged_snapshot", String.class))
				.containsExactlyInAnyOrder("DEFINITIVE", "CONSOLIDATED", "PROVISIONAL");
	}

	// ---- one source only -----------------------------------------------------------------------------------------

	@Test
	void aBatchRowAloneIsUnchanged() {
		batch(NATIONAL, "2026-08-31T21:30:00Z", "DEFINITIVE", 43979, 29);

		List<Map<String, Object>> rows = view();

		assertThat(rows).hasSize(1);
		assertThat(rows.getFirst()).containsEntry("quality", "DEFINITIVE").containsEntry("consumption", 43979);
		assertThat(instant(rows.getFirst())).isEqualTo(Instant.parse("2026-08-31T21:30:00Z"));
	}

	@Test
	void twoQuarterHoursBecomeOneHalfHourWithTheirAverage() {
		provisional(NATIONAL, "2026-09-15T12:00:00Z", 49260, 10);
		provisional(NATIONAL, "2026-09-15T12:15:00Z", 50223, 12);
		provisional(NATIONAL, "2026-09-15T12:30:00Z", 49492, 11);
		provisional(NATIONAL, "2026-09-15T12:45:00Z", 49206, 11);

		List<Map<String, Object>> rows = view();

		assertThat(rows).hasSize(2);
		assertThat(instant(rows.get(0))).isEqualTo(Instant.parse("2026-09-15T12:00:00Z"));
		// (49260 + 50223) / 2 = 49741.5, rounded
		assertThat(rows.get(0)).containsEntry("quality", "PROVISIONAL").containsEntry("consumption", 49742);
		assertThat((BigDecimal) rows.get(0).get("co2_intensity")).isEqualByComparingTo("11");
		assertThat(instant(rows.get(1))).isEqualTo(Instant.parse("2026-09-15T12:30:00Z"));
		// (49492 + 49206) / 2 = 49349
		assertThat(rows.get(1)).containsEntry("consumption", 49349);
	}

	@Test
	void aHalfFilledBucketIsPublishedAsItIs() {
		provisional(NATIONAL, "2026-09-15T12:00:00Z", 49260, 11);

		List<Map<String, Object>> rows = view();

		assertThat(rows).hasSize(1);
		assertThat(rows.getFirst()).containsEntry("consumption", 49260).containsEntry("quality", "PROVISIONAL");
	}

	// ---- mixing --------------------------------------------------------------------------------------------------

	@Test
	void aSeriesMixingBothSourcesHasTheRightQualityPerPeriodAndNoDuplicate() {
		batch(NATIONAL, "2026-08-31T21:00:00Z", "CONSOLIDATED", 45070, 29);
		batch(NATIONAL, "2026-08-31T21:30:00Z", "CONSOLIDATED", 43979, 29);
		provisional(NATIONAL, "2026-08-31T21:30:00Z", 43900, 30);
		provisional(NATIONAL, "2026-08-31T21:45:00Z", 43800, 30);
		provisional(NATIONAL, "2026-09-01T00:00:00Z", 40000, 28);
		provisional(NATIONAL, "2026-09-01T00:15:00Z", 40100, 28);

		List<Map<String, Object>> rows = view();

		assertThat(rows).extracting(row -> row.get("quality"))
				.containsExactly("CONSOLIDATED", "CONSOLIDATED", "PROVISIONAL");
		assertThat(rows).extracting(row -> row.get("consumption")).containsExactly(45070, 43979, 40050);
		assertThat(jdbc.queryForObject(
				"SELECT count(*) - count(DISTINCT (scope, observed_at)) FROM merged_snapshot", Integer.class)).isZero();
	}

	@Test
	void aHoleInTheBatchFallsBackToTheProvisionalRow() {
		// batch has the surrounding half hours but not 00:00Z (the autumn hole of cons-def)
		batch(NATIONAL, "2025-10-25T23:30:00Z", "DEFINITIVE", 40000, 20);
		batch(NATIONAL, "2025-10-26T01:00:00Z", "DEFINITIVE", 41000, 20);
		provisional(NATIONAL, "2025-10-26T00:00:00Z", 40500, 21);

		assertThat(view()).extracting(row -> row.get("quality"))
				.containsExactly("DEFINITIVE", "PROVISIONAL", "DEFINITIVE");
	}

	@Test
	void aBatchRowOfOneScopeNeverHidesTheProvisionalRowOfAnother() {
		batch(NATIONAL, "2026-09-15T12:00:00Z", "CONSOLIDATED", 49500, 12);
		provisional("region:11", "2026-09-15T12:00:00Z", 8000, 40);

		List<Map<String, Object>> rows = jdbc.queryForList(
				"SELECT scope, quality FROM merged_snapshot ORDER BY scope");

		assertThat(rows).hasSize(2);
		assertThat(rows.get(0)).containsEntry("scope", "national").containsEntry("quality", "CONSOLIDATED");
		assertThat(rows.get(1)).containsEntry("scope", "region:11").containsEntry("quality", "PROVISIONAL");
	}

	// ---- nulls and flags -----------------------------------------------------------------------------------------

	@Test
	void aMissingFigureUsesTheOtherQuarterAndStaysMissingWhenBothAreMissing() {
		jdbc.update("""
				INSERT INTO speed_snapshot (scope, observed_at, consumption, production_nuclear, production_wind)
				VALUES ('national', '2026-09-15T12:00:00Z', 49000, 30000, NULL),
				       ('national', '2026-09-15T12:15:00Z', 50000, NULL, NULL)
				""");

		Map<String, Object> row = view().getFirst();

		assertThat(row).containsEntry("production_nuclear", 30000);
		// NULL, not 0: a figure nobody reported is not a zero
		assertThat(row.get("production_wind")).isNull();
	}

	@Test
	void estimatedIsTrueWhenAnyQuarterIsEstimated() {
		jdbc.update("""
				INSERT INTO speed_snapshot (scope, observed_at, consumption, estimated)
				VALUES ('region:11', '2026-09-15T12:00:00Z', 8000, false),
				       ('region:11', '2026-09-15T12:15:00Z', 8100, true)
				""");

		assertThat(view().getFirst()).containsEntry("estimated", true);
	}

	@Test
	void aQuarterHourWithoutConsumptionIsNotAReading() {
		jdbc.update("""
				INSERT INTO speed_snapshot (scope, observed_at, consumption, production_nuclear)
				VALUES ('national', '2026-09-15T12:00:00Z', 49000, 30000),
				       ('national', '2026-09-15T12:15:00Z', NULL, 99999),
				       ('national', '2026-09-15T12:30:00Z', NULL, 88888)
				""");

		List<Map<String, Object>> rows = view();

		// the first half hour keeps only its measured quarter, the second has no reading at all
		assertThat(rows).hasSize(1);
		assertThat(rows.getFirst()).containsEntry("production_nuclear", 30000);
	}

	// ---- migration -----------------------------------------------------------------------------------------------

	@Test
	void theViewIsCreatedByTheMigrationAsGridApp() {
		assertThat(jdbc.queryForObject(
				"SELECT success FROM grid.flyway_schema_history WHERE version = '4'", Boolean.class)).isTrue();
		assertThat(jdbc.queryForObject(
				"SELECT viewowner FROM pg_views WHERE schemaname = 'grid' AND viewname = 'merged_snapshot'",
				String.class)).isEqualTo("grid_app");
	}

	// ---- helpers -------------------------------------------------------------------------------------------------

	private List<Map<String, Object>> view() {
		return jdbc.queryForList("SELECT * FROM merged_snapshot ORDER BY scope, observed_at");
	}

	private static Instant instant(Map<String, Object> row) {
		return ((Timestamp) row.get("observed_at")).toInstant();
	}

	private void provisional(String scope, String observedAt, int consumption, int co2) {
		jdbc.update("""
				INSERT INTO speed_snapshot (scope, observed_at, co2_intensity, consumption)
				VALUES (?, ?::timestamptz, ?, ?)
				""", scope, observedAt, co2, consumption);
	}

	private void batch(String scope, String observedAt, String quality, int consumption, int co2) {
		jdbc.update("""
				INSERT INTO batch_snapshot (scope, observed_at, quality, co2_intensity, consumption)
				VALUES (?, ?::timestamptz, ?, ?, ?)
				""", scope, observedAt, quality, co2, consumption);
	}
}
