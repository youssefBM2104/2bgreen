package com.twobgreen.gridintelligence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.twobgreen.gridintelligence.application.BatchSnapshotRepository;
import com.twobgreen.gridintelligence.domain.GridSnapshot;
import com.twobgreen.gridintelligence.domain.Production;
import com.twobgreen.gridintelligence.domain.Quality;

/** G3: the batch upsert, on the real TimescaleDB. The rules under test are the three acceptance criteria. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BatchSnapshotRepositoryTests {

	private static final Instant AT = Instant.parse("2026-08-31T21:30:00Z");

	@Autowired
	BatchSnapshotRepository repository;

	@Autowired
	JdbcTemplate jdbc;

	@BeforeEach
	void emptyTable() {
		jdbc.update("DELETE FROM batch_snapshot");
	}

	@Test
	void newRowsAreInserted() {
		assertThat(repository.upsert(List.of(reading(AT, Quality.CONSOLIDATED, 43979)))).isEqualTo(1);

		assertThat(jdbc.queryForObject("SELECT quality FROM batch_snapshot", String.class)).isEqualTo("CONSOLIDATED");
		assertThat(jdbc.queryForObject("SELECT production_nuclear FROM batch_snapshot", Integer.class)).isEqualTo(37002);
	}

	@Test
	void aSecondRunOverTheSameDataChangesNothing() {
		List<GridSnapshot> year = List.of(reading(AT, Quality.CONSOLIDATED, 43979),
				reading(AT.minusSeconds(1800), Quality.CONSOLIDATED, 45070));
		repository.upsert(year);
		Timestamp storedAt = jdbc.queryForObject("SELECT max(stored_at) FROM batch_snapshot", Timestamp.class);

		int written = repository.upsert(year);

		assertThat(written).isZero();
		// Not even stored_at moves: "nothing changed" has to be visible in the table
		assertThat(jdbc.queryForObject("SELECT max(stored_at) FROM batch_snapshot", Timestamp.class))
				.isEqualTo(storedAt);
		assertThat(count()).isEqualTo(2);
	}

	@Test
	void aRowThatBecomesDefinitiveIsUpdated() {
		repository.upsert(List.of(reading(AT, Quality.CONSOLIDATED, 43979)));
		Timestamp storedAt = jdbc.queryForObject("SELECT stored_at FROM batch_snapshot", Timestamp.class);

		// ODRÉ may revise a figure when it freezes the year: same instant, new quality, new value
		int written = repository.upsert(List.of(reading(AT, Quality.DEFINITIVE, 43985)));

		assertThat(written).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT quality FROM batch_snapshot", String.class)).isEqualTo("DEFINITIVE");
		assertThat(jdbc.queryForObject("SELECT consumption FROM batch_snapshot", Integer.class)).isEqualTo(43985);
		assertThat(jdbc.queryForObject("SELECT stored_at FROM batch_snapshot", Timestamp.class)).isAfter(storedAt);
	}

	@Test
	void aRowThatOnlyChangesQualityIsStillUpdated() {
		repository.upsert(List.of(reading(AT, Quality.CONSOLIDATED, 43979)));

		assertThat(repository.upsert(List.of(reading(AT, Quality.DEFINITIVE, 43979)))).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT quality FROM batch_snapshot", String.class)).isEqualTo("DEFINITIVE");
	}

	@Test
	void aDefinitiveRowIsNeverDowngraded() {
		repository.upsert(List.of(reading(AT, Quality.DEFINITIVE, 43985)));

		int written = repository.upsert(List.of(reading(AT, Quality.CONSOLIDATED, 43979)));

		assertThat(written).isZero();
		assertThat(jdbc.queryForObject("SELECT quality FROM batch_snapshot", String.class)).isEqualTo("DEFINITIVE");
		assertThat(jdbc.queryForObject("SELECT consumption FROM batch_snapshot", Integer.class)).isEqualTo(43985);
	}

	@Test
	void aRevisedValueAtTheSameQualityIsUpdated() {
		repository.upsert(List.of(reading(AT, Quality.CONSOLIDATED, 43979)));

		assertThat(repository.upsert(List.of(reading(AT, Quality.CONSOLIDATED, 43980)))).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT consumption FROM batch_snapshot", Integer.class)).isEqualTo(43980);
	}

	@Test
	void missingValuesAreStoredAsNullNeverZeroAndRepeatedWithoutChange() {
		GridSnapshot sparse = new GridSnapshot("national", AT, null, 43979, null, null, null, null,
				Quality.CONSOLIDATED, false);
		repository.upsert(List.of(sparse));

		assertThat(repository.upsert(List.of(sparse))).isZero();
		assertThat(jdbc.queryForObject("""
				SELECT co2_intensity IS NULL AND production_nuclear IS NULL AND pumping IS NULL FROM batch_snapshot
				""", Boolean.class)).isTrue();
	}

	@Test
	void aFigureThatDisappearsBecomesNull() {
		repository.upsert(List.of(reading(AT, Quality.CONSOLIDATED, 43979)));

		repository.upsert(List.of(new GridSnapshot("national", AT, null, 43979, null, null, null, null,
				Quality.CONSOLIDATED, false)));

		assertThat(jdbc.queryForObject("SELECT co2_intensity IS NULL FROM batch_snapshot", Boolean.class)).isTrue();
	}

	@Test
	void aLoadLargerThanOneJdbcBatchIsFullyWritten() {
		List<GridSnapshot> year = new ArrayList<>();
		for (int i = 0; i < 2500; i++) {
			year.add(reading(AT.minusSeconds(1800L * i), Quality.CONSOLIDATED, 40000 + i));
		}

		assertThat(repository.upsert(year)).isEqualTo(2500);
		assertThat(repository.upsert(year)).isZero();
		assertThat(count()).isEqualTo(2500);
	}

	@Test
	void emptyListWritesNothing() {
		assertThat(repository.upsert(List.of())).isZero();
	}

	private long count() {
		return jdbc.queryForObject("SELECT count(*) FROM batch_snapshot", Long.class);
	}

	private static GridSnapshot reading(Instant at, Quality quality, int consumption) {
		return new GridSnapshot("national", at, new BigDecimal("29"), consumption, 43000, 44200,
				new Production(37002, 3219, 15, 55, null, 3758, 1, 4311, 1179), -50, quality, false);
	}
}
