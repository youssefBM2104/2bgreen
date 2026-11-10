package com.twobgreen.platform.regional.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import com.twobgreen.platform.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class RegionalUserDataTests {

	/** PostgreSQL keeps microseconds. */
	private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

	/** A fresh user per test: tests share one database and must not see each other's rows. */
	private final String user = UUID.randomUUID().toString();

	private static final LocalDate DAY = LocalDate.of(2026, 11, 10);

	@Autowired
	private TargetRepository targets;

	@Autowired
	private TargetProgressRepository progress;

	@Test
	void targetIsStoredAndReadBack() {
		Target saved = targets.save(target(new BigDecimal("5")));

		Target read = targets.findByIdAndUserId(saved.id(), user).orElseThrow();
		assertThat(read.indicator()).isEqualTo(TargetIndicator.RENEWABLE_SHARE);
		assertThat(read.referenceValue()).isEqualByComparingTo("31.4");
		assertThat(read.targetValue()).isEqualByComparingTo("45");
		assertThat(read.deadline()).isEqualTo(LocalDate.of(2030, 12, 31));
		assertThat(read.createdAt()).isEqualTo(NOW);
		assertThat(targets.findByIdAndUserId(saved.id(), "someone-else")).isEmpty();
	}

	@Test
	void targetWithoutToleranceHasItsPaceAlertOff() {
		Target saved = targets.save(target(null));

		assertThat(targets.findById(saved.id()).orElseThrow().tolerance()).isNull();
	}

	@Test
	void wrongRegionCodeOrToleranceIsRefusedByTheDatabase() {
		assertThatThrownBy(() -> targets.save(new Target(null, user, "PACA", TargetIndicator.COVERAGE_RATE, 2024,
				BigDecimal.ONE, BigDecimal.TEN, DAY, null, NOW))).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> targets.save(target(BigDecimal.ZERO)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void progressIsReadInDayOrder() {
		Target target = targets.save(target(null));
		progress.record(target.id(), DAY, new BigDecimal("33.1"), TargetStatus.ON_TRACK.name());
		progress.record(target.id(), DAY.minusDays(1), new BigDecimal("33.0"), TargetStatus.LATE.name());

		assertThat(progress.findByTargetIdOrderByDay(target.id()))
				.extracting(TargetProgress::day, TargetProgress::status)
				.containsExactly(org.assertj.core.groups.Tuple.tuple(DAY.minusDays(1), TargetStatus.LATE),
						org.assertj.core.groups.Tuple.tuple(DAY, TargetStatus.ON_TRACK));
	}

	@Test
	void recalculatingADayReplacesItsRow() {
		Target target = targets.save(target(null));
		progress.record(target.id(), DAY, new BigDecimal("33.1"), TargetStatus.LATE.name());

		progress.record(target.id(), DAY, new BigDecimal("34.2"), TargetStatus.ON_TRACK.name());

		assertThat(progress.findByTargetIdOrderByDay(target.id())).singleElement().satisfies(row -> {
			assertThat(row.value()).isEqualByComparingTo("34.2");
			assertThat(row.status()).isEqualTo(TargetStatus.ON_TRACK);
		});
	}

	@Test
	void sameDayTwiceThroughSaveIsRefused() {
		Target target = targets.save(target(null));
		progress.save(new TargetProgress(null, target.id(), DAY, BigDecimal.ONE, TargetStatus.AHEAD));

		assertThatThrownBy(
				() -> progress.save(new TargetProgress(null, target.id(), DAY, BigDecimal.TEN, TargetStatus.LATE)))
				.isInstanceOf(DuplicateKeyException.class);
	}

	@Test
	void progressOfAnUnknownTargetIsRefused() {
		assertThatThrownBy(() -> progress.record(-1, DAY, BigDecimal.ONE, TargetStatus.AHEAD.name()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void progressGoesWithItsTarget() {
		Target target = targets.save(target(null));
		progress.record(target.id(), DAY, BigDecimal.ONE, TargetStatus.AHEAD.name());

		targets.deleteById(target.id());

		assertThat(progress.findByTargetIdOrderByDay(target.id())).isEmpty();
	}

	@Test
	void deletingAUserRemovesTheirTargetsAndTheHistory() {
		Target target = targets.save(target(null));
		progress.record(target.id(), DAY, BigDecimal.ONE, TargetStatus.AHEAD.name());

		assertThat(targets.deleteAllOfUser(user)).isEqualTo(1);

		assertThat(targets.findByUserIdOrderById(user)).isEmpty();
		assertThat(progress.findByTargetIdOrderByDay(target.id())).isEmpty();
	}

	private Target target(BigDecimal tolerance) {
		return new Target(null, user, "93", TargetIndicator.RENEWABLE_SHARE, 2024, new BigDecimal("31.4"),
				new BigDecimal("45"), LocalDate.of(2030, 12, 31), tolerance, NOW);
	}
}
