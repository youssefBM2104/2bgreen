package com.twobgreen.gridintelligence;

import static org.assertj.core.api.Assertions.assertThat;

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

import com.twobgreen.gridintelligence.application.RegionalConsolidatedOdreGateway;
import com.twobgreen.gridintelligence.application.SyncNationalConsolidated;
import com.twobgreen.gridintelligence.application.SyncRegionalConsolidated;
import com.twobgreen.gridintelligence.application.YearQualityCount;
import com.twobgreen.gridintelligence.domain.GridSnapshot;
import com.twobgreen.gridintelligence.domain.Production;
import com.twobgreen.gridintelligence.domain.Quality;

/**
 * G6 end to end on the real TimescaleDB, with a simulated regional ODRÉ: the real gateway is covered by
 * {@code RegionalConsolidatedGatewayTests}. One test per acceptance criterion, plus what G6 adds to G3.
 */
@Import({ TestcontainersConfiguration.class, RegionalConsolidatedBatchIntegrationTests.SimulatedOdreConfig.class })
@SpringBootTest(properties = { "batch.initial-delay=PT1H", "batch.regional.first-year=2021" })
@ActiveProfiles("batch")
class RegionalConsolidatedBatchIntegrationTests {

	@Autowired
	SimulatedRegionalOdre odre;

	@Autowired
	SyncRegionalConsolidated sync;

	@Autowired
	SyncNationalConsolidated national;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	ApplicationContext context;

	@BeforeEach
	void cleanSlate() {
		jdbc.update("DELETE FROM batch_snapshot");
		jdbc.update("DELETE FROM batch_sync_state");
		odre.reset();
		odre.year(2020, Quality.DEFINITIVE, 3000);
		odre.year(2021, Quality.DEFINITIVE, 4000, 4010);
		odre.year(2025, Quality.DEFINITIVE, 4500);
		odre.year(2026, Quality.CONSOLIDATED, 4600, 4610);
	}

	@Test
	void theRegionalBatchIsPartOfTheBatchProfile() {
		assertThat(context.containsBean("regionalConsolidatedBatch")).isTrue();
	}

	@Test
	void theHistoryFrom2021IsLoadedAndNothingBefore() {
		SyncRegionalConsolidated.Result result = sync.sync();

		assertThat(result.yearsLoaded()).containsExactly(2021, 2025, 2026);
		assertThat(odre.downloads).doesNotContain(2020);
		// two regions per period
		assertThat(count()).isEqualTo(10);
		assertThat(jdbc.queryForObject("SELECT min(observed_at) FROM batch_snapshot", Timestamp.class).toInstant())
				.isEqualTo(Instant.parse("2021-01-01T00:00:00Z"));
		assertThat(jdbc.queryForObject("SELECT count(DISTINCT scope) FROM batch_snapshot", Long.class)).isEqualTo(2);
	}

	@Test
	void everyRegionalReadingGetsItsEstimatedCo2() {
		sync.sync();

		assertThat(jdbc.queryForObject(
				"SELECT count(*) FROM batch_snapshot WHERE co2_intensity IS NULL OR NOT estimated", Long.class))
				.isZero();
		// (429 x 1173 + 494 x 79) / 6241 = 86.88, the mix of the fixture regional-tr-measured.json
		assertThat(jdbc.queryForObject("SELECT DISTINCT co2_intensity FROM batch_snapshot", Integer.class))
				.isEqualTo(87);
	}

	@Test
	void aSecondRunChangesNothingAndDownloadsNothing() {
		sync.sync();
		Timestamp storedAt = jdbc.queryForObject("SELECT max(stored_at) FROM batch_snapshot", Timestamp.class);
		odre.downloads.clear();

		SyncRegionalConsolidated.Result result = sync.sync();

		assertThat(result.yearsLoaded()).isEmpty();
		assertThat(result.rowsWritten()).isZero();
		assertThat(odre.downloads).isEmpty();
		assertThat(jdbc.queryForObject("SELECT max(stored_at) FROM batch_snapshot", Timestamp.class))
				.isEqualTo(storedAt);
		assertThat(count()).isEqualTo(10);
	}

	@Test
	void aYearThatTurnsDefinitiveIsReloadedWithTheSameUpsertRules() {
		sync.sync();
		odre.downloads.clear();

		odre.year(2026, Quality.DEFINITIVE, 4600, 4611);
		SyncRegionalConsolidated.Result result = sync.sync();

		assertThat(result.yearsLoaded()).containsExactly(2026);
		assertThat(odre.downloads).containsExactly(2026);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM batch_snapshot WHERE quality = 'CONSOLIDATED'",
				Long.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT consumption FROM batch_snapshot WHERE scope = 'region:93' "
				+ "AND observed_at = '2026-01-01T00:30:00Z'", Integer.class)).isEqualTo(4611);
	}

	@Test
	void theRegionalStateIsKeptApartFromTheNationalOne() {
		sync.sync();

		assertThat(jdbc.queryForList("SELECT DISTINCT scope FROM batch_sync_state", String.class))
				.containsExactly("regional");
		// The national batch still sees an empty state of its own: its own years are not marked as loaded
		assertThat(jdbc.queryForObject("SELECT count(*) FROM batch_sync_state WHERE scope = 'national'", Long.class))
				.isZero();
		assertThat(national).isNotNull();
	}

	private long count() {
		return jdbc.queryForObject("SELECT count(*) FROM batch_snapshot", Long.class);
	}

	/** Regional ODRÉ as the batch sees it: two regions, one reading every 30 min from 1 January. */
	static final class SimulatedRegionalOdre implements RegionalConsolidatedOdreGateway {

		private final Map<Integer, List<GridSnapshot>> readings = new TreeMap<>();

		final List<Integer> downloads = new ArrayList<>();

		void reset() {
			readings.clear();
			downloads.clear();
		}

		void year(int year, Quality quality, int... consumptions) {
			List<GridSnapshot> rows = new ArrayList<>();
			Instant start = Instant.parse(year + "-01-01T00:00:00Z");
			for (String scope : List.of("region:93", "region:76")) {
				for (int i = 0; i < consumptions.length; i++) {
					rows.add(new GridSnapshot(scope, start.plusSeconds(1800L * i), null, consumptions[i], null, null,
							new Production(3562, null, null, null, 1173, 1374, 50, 3, 79), 0, quality, false));
				}
			}
			readings.put(year, rows);
		}

		@Override
		public List<YearQualityCount> regionalCounts() {
			return readings.entrySet().stream()
					.map(year -> new YearQualityCount(year.getKey(), year.getValue().getFirst().quality(),
							year.getValue().size()))
					.toList();
		}

		@Override
		public List<GridSnapshot> regionalReadings(int year) {
			downloads.add(year);
			return readings.get(year);
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class SimulatedOdreConfig {

		@Bean
		@Primary
		SimulatedRegionalOdre simulatedRegionalOdre() {
			return new SimulatedRegionalOdre();
		}
	}
}
