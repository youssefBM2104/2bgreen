package com.twobgreen.gridintelligence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.twobgreen.gridintelligence.application.SpeedSnapshotRepository;
import com.twobgreen.gridintelligence.domain.GridSnapshot;
import com.twobgreen.gridintelligence.domain.Production;
import com.twobgreen.gridintelligence.domain.Quality;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SpeedSnapshotRepositoryTests {

	// Values of the committed fixture national-tr-measured.json
	private static final Instant OBSERVED_AT = Instant.parse("2026-10-20T14:45:00Z");

	@Autowired
	SpeedSnapshotRepository repository;

	@Autowired
	JdbcTemplate jdbc;

	@BeforeEach
	void emptyTable() {
		jdbc.update("DELETE FROM speed_snapshot");
	}

	@Test
	void sameSnapshotTwiceKeepsOneRow() {
		repository.upsert(measured(29));
		repository.upsert(measured(29));

		assertThat(count()).isEqualTo(1);
	}

	@Test
	void secondSnapshotReplacesValuesAndRefreshesStoredAt() {
		Instant first = repository.upsert(measured(29));
		Instant second = repository.upsert(measured(31));

		assertThat(second).isAfter(first);
		assertThat(jdbc.queryForObject("SELECT co2_intensity FROM speed_snapshot", Integer.class)).isEqualTo(31);
		assertThat(jdbc.queryForObject("SELECT production_nuclear FROM speed_snapshot", Integer.class)).isEqualTo(38499);
	}

	@Test
	void missingValuesAreStoredAsNullNeverZero() {
		repository.upsert(measured(29));

		repository.upsert(new GridSnapshot("national", OBSERVED_AT, null, null, null, null, null, null,
				Quality.PROVISIONAL, false));

		assertThat(count()).isEqualTo(1);
		assertThat(jdbc.queryForObject("""
				SELECT co2_intensity IS NULL AND consumption IS NULL AND production_nuclear IS NULL
				       AND pumping IS NULL
				FROM speed_snapshot
				""", Boolean.class)).isTrue();
	}

	@Test
	void differentScopesAreDifferentRows() {
		repository.upsert(measured(29));
		repository.upsert(new GridSnapshot("region:76", OBSERVED_AT, null, 3805, null, null,
				new Production(1257, null, null, null, 1, 376, 2, 1167, 68), 0, Quality.PROVISIONAL, true));

		assertThat(count()).isEqualTo(2);
		assertThat(jdbc.queryForObject(
				"SELECT estimated FROM speed_snapshot WHERE scope = 'region:76'", Boolean.class)).isTrue();
	}

	@Test
	void latestObservedAtIsEmptyWhenNothingIsStored() {
		assertThat(repository.latestObservedAt("national")).isEmpty();
	}

	@Test
	void latestObservedAtIsTheNewestReadingOfThatScopeOnly() {
		repository.upsert(measured(29));
		repository.upsert(new GridSnapshot("national", OBSERVED_AT.minusSeconds(900), null, 49000, null, null, null,
				null, Quality.PROVISIONAL, false));
		repository.upsert(new GridSnapshot("region:76", OBSERVED_AT.plusSeconds(900), null, 3805, null, null, null,
				null, Quality.PROVISIONAL, true));

		assertThat(repository.latestObservedAt("national")).contains(OBSERVED_AT);
	}

	private int count() {
		return jdbc.queryForObject("SELECT count(*) FROM speed_snapshot", Integer.class);
	}

	private static GridSnapshot measured(int co2) {
		return new GridSnapshot("national", OBSERVED_AT, BigDecimal.valueOf(co2), 49970, 49950, 49650,
				new Production(38499, 2632, 0, 37, null, 6225, 4656, 3768, 941), 0, Quality.PROVISIONAL, false);
	}
}
