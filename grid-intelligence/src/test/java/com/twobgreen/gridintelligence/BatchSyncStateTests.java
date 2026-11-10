package com.twobgreen.gridintelligence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.twobgreen.gridintelligence.application.BatchSyncState;
import com.twobgreen.gridintelligence.application.YearQualityCount;
import com.twobgreen.gridintelligence.domain.Quality;

/** V3 (batch_sync_state) and the port that reads and writes it. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BatchSyncStateTests {

	@Autowired
	BatchSyncState state;

	@Autowired
	JdbcTemplate jdbc;

	@BeforeEach
	void emptyTable() {
		jdbc.update("DELETE FROM batch_sync_state");
	}

	@Test
	void migrationAppliedAsGridApp() {
		assertThat(jdbc.queryForObject(
				"SELECT success FROM grid.flyway_schema_history WHERE version = '3'", Boolean.class)).isTrue();
		assertThat(jdbc.queryForObject(
				"SELECT tableowner FROM pg_tables WHERE schemaname = 'grid' AND tablename = 'batch_sync_state'",
				String.class)).isEqualTo("grid_app");
	}

	@Test
	void nothingIsLoadedAtFirst() {
		assertThat(state.loaded()).isEmpty();
	}

	@Test
	void aYearWithTwoQualitiesIsStoredAndReadBack() {
		// Quality flips on Paris years, counts are by UTC year: 2025 really holds both
		state.markLoaded(2025, List.of(new YearQualityCount(2025, Quality.CONSOLIDATED, 4),
				new YearQualityCount(2025, Quality.DEFINITIVE, 35036)));

		assertThat(state.loaded()).containsExactly(
				new YearQualityCount(2025, Quality.CONSOLIDATED, 4),
				new YearQualityCount(2025, Quality.DEFINITIVE, 35036));
	}

	@Test
	void markingAYearAgainReplacesItAndLeavesTheOthers() {
		state.markLoaded(2025, List.of(new YearQualityCount(2025, Quality.CONSOLIDATED, 4),
				new YearQualityCount(2025, Quality.DEFINITIVE, 35036)));
		state.markLoaded(2026, List.of(new YearQualityCount(2026, Quality.CONSOLIDATED, 23324)));

		// ODRÉ froze the first hour of 2026: the consolidated group of 2025 is gone and must not linger
		state.markLoaded(2025, List.of(new YearQualityCount(2025, Quality.DEFINITIVE, 35040)));

		assertThat(state.loaded()).containsExactly(
				new YearQualityCount(2025, Quality.DEFINITIVE, 35040),
				new YearQualityCount(2026, Quality.CONSOLIDATED, 23324));
	}

	@Test
	void aCountOfAnotherYearIsRefusedAndChangesNothing() {
		state.markLoaded(2025, List.of(new YearQualityCount(2025, Quality.DEFINITIVE, 35040)));

		assertThatThrownBy(() -> state.markLoaded(2025, List.of(new YearQualityCount(2026, Quality.CONSOLIDATED, 1))))
				.isInstanceOf(IllegalArgumentException.class);

		// The delete is rolled back with the failed insert: the year is still recorded as it was
		assertThat(state.loaded()).containsExactly(new YearQualityCount(2025, Quality.DEFINITIVE, 35040));
	}

	@Test
	void aProvisionalCountDoesNotExist() {
		assertThatThrownBy(() -> new YearQualityCount(2026, Quality.PROVISIONAL, 1))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
