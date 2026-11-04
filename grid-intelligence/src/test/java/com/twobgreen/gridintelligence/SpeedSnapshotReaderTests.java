package com.twobgreen.gridintelligence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.twobgreen.gridintelligence.application.SnapshotReader;
import com.twobgreen.gridintelligence.application.SpeedSnapshotRepository;
import com.twobgreen.gridintelligence.application.StoredSnapshot;
import com.twobgreen.gridintelligence.domain.GridSnapshot;
import com.twobgreen.gridintelligence.domain.Production;
import com.twobgreen.gridintelligence.domain.Quality;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SpeedSnapshotReaderTests {

	// Values of the committed fixture national-tr-measured.json
	private static final Instant OBSERVED_AT = Instant.parse("2026-10-20T14:45:00Z");

	@Autowired
	SnapshotReader reader;

	@Autowired
	SpeedSnapshotRepository repository;

	@Autowired
	JdbcTemplate jdbc;

	@BeforeEach
	void emptyTable() {
		jdbc.update("DELETE FROM speed_snapshot");
	}

	@Test
	void nothingStoredGivesNothing() {
		assertThat(reader.latestWithMeasures("national")).isEmpty();
	}

	@Test
	void returnsTheSnapshotWithItsValuesQualityAndStoredAt() {
		Instant storedAt = repository.upsert(measured("national", OBSERVED_AT, 49970));

		StoredSnapshot stored = reader.latestWithMeasures("national").orElseThrow();

		assertThat(stored.storedAt()).isEqualTo(storedAt);
		assertThat(stored.snapshot().scope()).isEqualTo("national");
		assertThat(stored.snapshot().observedAt()).isEqualTo(OBSERVED_AT);
		assertThat(stored.snapshot().co2Intensity()).isEqualByComparingTo(BigDecimal.valueOf(29));
		assertThat(stored.snapshot().consumption()).isEqualTo(49970);
		assertThat(stored.snapshot().consumptionForecastDay()).isEqualTo(49950);
		assertThat(stored.snapshot().consumptionForecastDayBefore()).isEqualTo(49650);
		assertThat(stored.snapshot().production())
				.isEqualTo(new Production(38499, 2632, 0, 37, null, 6225, 4656, 3768, 941));
		assertThat(stored.snapshot().pumping()).isZero();
		assertThat(stored.snapshot().estimated()).isFalse();
		assertThat(stored.snapshot().quality()).isEqualTo(Quality.PROVISIONAL);
	}

	@Test
	void returnsTheNewestReading() {
		repository.upsert(measured("national", OBSERVED_AT.minusSeconds(900), 49000));
		repository.upsert(measured("national", OBSERVED_AT, 49970));
		repository.upsert(measured("national", OBSERVED_AT.minusSeconds(1800), 48000));

		assertThat(reader.latestWithMeasures("national").orElseThrow().snapshot().observedAt()).isEqualTo(OBSERVED_AT);
	}

	@Test
	void aForecastOnlyRowIsNotAReading() {
		repository.upsert(measured("national", OBSERVED_AT, 49970));
		// Rest of today: forecasts set, every measure null. Newer than the reading.
		repository.upsert(new GridSnapshot("national", OBSERVED_AT.plusSeconds(900), null, null, 49900, 49600, null, null,
				Quality.PROVISIONAL, false));
		// Tomorrow: only the forecast of the day before
		repository.upsert(new GridSnapshot("national", OBSERVED_AT.plusSeconds(86400), null, null, null, 49000, null,
				null, Quality.PROVISIONAL, false));

		assertThat(reader.latestWithMeasures("national").orElseThrow().snapshot().observedAt()).isEqualTo(OBSERVED_AT);
	}

	@Test
	void onlyForecastRowsGiveNothing() {
		repository.upsert(new GridSnapshot("national", OBSERVED_AT, null, null, 49900, 49600, null, null,
				Quality.PROVISIONAL, false));

		assertThat(reader.latestWithMeasures("national")).isEmpty();
	}

	@Test
	void otherScopesAreIgnored() {
		repository.upsert(measured("national", OBSERVED_AT, 49970));
		repository.upsert(measured("region:76", OBSERVED_AT.plusSeconds(900), 3805));

		assertThat(reader.latestWithMeasures("national").orElseThrow().snapshot().observedAt()).isEqualTo(OBSERVED_AT);
		assertThat(reader.latestWithMeasures("region:76").orElseThrow().snapshot().consumption()).isEqualTo(3805);
		assertThat(reader.latestWithMeasures("region:11")).isEmpty();
	}

	@Test
	void missingValuesStayMissingNeverZero() {
		repository.upsert(new GridSnapshot("national", OBSERVED_AT, null, 49970, null, null, null, null,
				Quality.PROVISIONAL, false));

		GridSnapshot snapshot = reader.latestWithMeasures("national").orElseThrow().snapshot();

		assertThat(snapshot.co2Intensity()).isNull();
		assertThat(snapshot.consumptionForecastDay()).isNull();
		assertThat(snapshot.consumptionForecastDayBefore()).isNull();
		assertThat(snapshot.pumping()).isNull();
		assertThat(snapshot.production()).isEqualTo(Production.NONE);
	}

	@Test
	void storedAtIsTheColumn() {
		repository.upsert(measured("national", OBSERVED_AT, 49970));
		Instant column = jdbc.queryForObject("SELECT stored_at FROM speed_snapshot", Timestamp.class).toInstant();

		assertThat(reader.latestWithMeasures("national").orElseThrow().storedAt()).isEqualTo(column);
	}

	private static GridSnapshot measured(String scope, Instant observedAt, int consumption) {
		return new GridSnapshot(scope, observedAt, BigDecimal.valueOf(29), consumption, 49950, 49650,
				new Production(38499, 2632, 0, 37, null, 6225, 4656, 3768, 941), 0, Quality.PROVISIONAL, false);
	}
}
