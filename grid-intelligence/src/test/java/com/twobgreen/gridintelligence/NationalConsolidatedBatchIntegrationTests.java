package com.twobgreen.gridintelligence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.twobgreen.gridintelligence.application.ConsolidatedOdreGateway;
import com.twobgreen.gridintelligence.application.SyncNationalConsolidated;
import com.twobgreen.gridintelligence.application.YearQualityCount;
import com.twobgreen.gridintelligence.domain.GridSnapshot;
import com.twobgreen.gridintelligence.domain.Production;
import com.twobgreen.gridintelligence.domain.Quality;

/**
 * G3 end to end on the real TimescaleDB, with a simulated ODRÉ: the real gateway is covered by the ODRÉ tests, here
 * what matters is what the batch does with its answers. One test per acceptance criterion of the issue.
 */
@Import({ TestcontainersConfiguration.class, NationalConsolidatedBatchIntegrationTests.SimulatedOdreConfig.class })
@SpringBootTest(properties = {
		// The test drives the runs; the scheduled one must not run in between
		"batch.initial-delay=PT1H"
})
@ActiveProfiles("batch")
class NationalConsolidatedBatchIntegrationTests {

	@Autowired
	SimulatedConsolidatedOdre odre;

	@Autowired
	SyncNationalConsolidated sync;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	ApplicationContext context;

	@BeforeEach
	void cleanSlate() {
		jdbc.update("DELETE FROM batch_snapshot");
		jdbc.update("DELETE FROM batch_sync_state");
		odre.reset();
		odre.year(2024, Quality.DEFINITIVE, 41000, 41010, 41020);
		odre.year(2025, Quality.CONSOLIDATED, 42000, 42010);
	}

	@Test
	void theBatchIsPartOfTheBatchProfile() {
		assertThat(context.containsBean("nationalConsolidatedBatch")).isTrue();
	}

	@Test
	void afterTheInitialLoadTheTableHoldsEveryYearOdreHas() {
		SyncNationalConsolidated.Result result = sync.sync();

		assertThat(result.yearsLoaded()).containsExactly(2024, 2025);
		assertThat(result.rowsWritten()).isEqualTo(5);
		assertThat(count()).isEqualTo(5);
		assertThat(jdbc.queryForObject("SELECT min(observed_at) FROM batch_snapshot", Timestamp.class).toInstant())
				.isEqualTo(Instant.parse("2024-01-01T00:00:00Z"));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM batch_snapshot WHERE quality = 'DEFINITIVE'",
				Long.class)).isEqualTo(3);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM batch_sync_state", Long.class)).isEqualTo(2);
	}

	@Test
	void aSecondRunChangesNothingAndDownloadsNothing() {
		sync.sync();
		Timestamp storedAt = jdbc.queryForObject("SELECT max(stored_at) FROM batch_snapshot", Timestamp.class);
		odre.downloads.clear();

		SyncNationalConsolidated.Result result = sync.sync();

		assertThat(result.yearsLoaded()).isEmpty();
		assertThat(result.rowsWritten()).isZero();
		assertThat(odre.downloads).isEmpty();
		assertThat(jdbc.queryForObject("SELECT max(stored_at) FROM batch_snapshot", Timestamp.class))
				.isEqualTo(storedAt);
		assertThat(count()).isEqualTo(5);
	}

	@Test
	void aYearThatTurnsConsolidatedToDefinitiveIsUpdatedAndTheOtherIsNotTouched() {
		sync.sync();
		Timestamp untouched = jdbc.queryForObject(
				"SELECT max(stored_at) FROM batch_snapshot WHERE observed_at < '2025-01-01'", Timestamp.class);
		odre.downloads.clear();

		// ODRÉ freezes 2025, and revises one figure on the way
		odre.year(2025, Quality.DEFINITIVE, 42000, 42011);

		SyncNationalConsolidated.Result result = sync.sync();

		assertThat(result.yearsLoaded()).containsExactly(2025);
		assertThat(odre.downloads).containsExactly(2025);
		assertThat(result.rowsWritten()).isEqualTo(2);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM batch_snapshot WHERE quality = 'DEFINITIVE'",
				Long.class)).isEqualTo(5);
		assertThat(jdbc.queryForObject(
				"SELECT consumption FROM batch_snapshot WHERE observed_at = '2025-01-01T00:30:00Z'", Integer.class))
				.isEqualTo(42011);
		assertThat(jdbc.queryForObject(
				"SELECT max(stored_at) FROM batch_snapshot WHERE observed_at < '2025-01-01'", Timestamp.class))
				.isEqualTo(untouched);
	}

	@Test
	void aStaleConsolidatedAnswerNeverDowngradesAFrozenYear() {
		sync.sync();

		// ODRÉ serves an older consolidated copy of 2024 (a stale replica): the counts differ, the year is read
		odre.year(2024, Quality.CONSOLIDATED, 1, 2, 3);
		sync.sync();

		assertThat(jdbc.queryForObject("SELECT count(*) FROM batch_snapshot WHERE quality = 'DEFINITIVE'",
				Long.class)).isEqualTo(3);
		assertThat(jdbc.queryForObject(
				"SELECT consumption FROM batch_snapshot WHERE observed_at = '2024-01-01T00:00:00Z'", Integer.class))
				.isEqualTo(41000);
	}

	private long count() {
		return jdbc.queryForObject("SELECT count(*) FROM batch_snapshot", Long.class);
	}

	/** ODRÉ as the batch sees it: counts per year, readings per year, and the log of what was downloaded. */
	static final class SimulatedConsolidatedOdre implements ConsolidatedOdreGateway {

		private final Map<Integer, List<GridSnapshot>> readings = new TreeMap<>();

		final List<Integer> downloads = new ArrayList<>();

		void reset() {
			readings.clear();
			downloads.clear();
		}

		/** The year as ODRÉ holds it now: one reading every 30 min from 1 January, with these consumptions. */
		void year(int year, Quality quality, int... consumptions) {
			List<GridSnapshot> rows = new ArrayList<>();
			Instant start = Instant.parse(year + "-01-01T00:00:00Z");
			for (int i = 0; i < consumptions.length; i++) {
				rows.add(new GridSnapshot("national", start.plusSeconds(1800L * i), new BigDecimal("29"),
						consumptions[i], 43000, 44200, new Production(37002, 3219, 15, 55, null, 3758, 1, 4311, 1179),
						-50, quality, false));
			}
			readings.put(year, rows);
		}

		@Override
		public List<YearQualityCount> nationalCounts() {
			return readings.entrySet().stream()
					.map(year -> new YearQualityCount(year.getKey(), year.getValue().getFirst().quality(),
							year.getValue().size()))
					.toList();
		}

		@Override
		public List<GridSnapshot> nationalReadings(int year) {
			downloads.add(year);
			return readings.get(year);
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class SimulatedOdreConfig {

		@Bean
		@Primary
		SimulatedConsolidatedOdre simulatedConsolidatedOdre() {
			return new SimulatedConsolidatedOdre();
		}
	}
}
