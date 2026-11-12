package com.twobgreen.platform.regional.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/** The calculation of the targets (U13) on series built by hand. */
class TargetCalculationTests {

	/** Created on 2026-01-01 (Paris), deadline 2030-01-01: 1461 days. */
	private static final Instant CREATED = Instant.parse("2025-12-31T23:30:00Z");

	private static final LocalDate DEADLINE = LocalDate.of(2030, 1, 1);

	private static Target target(TargetIndicator indicator, String reference, String goal, String tolerance) {
		return new Target(1L, "user", "93", indicator, 2025, new BigDecimal(reference), new BigDecimal(goal), DEADLINE,
				tolerance == null ? null : new BigDecimal(tolerance), CREATED);
	}

	private static EnergyBalance reading(int consumption, int nuclear, int thermal, int wind, int solar, int hydro,
			int bioenergy) {
		return EnergyBalance.of(consumption, nuclear, thermal, null, null, null, wind, solar, hydro, bioenergy);
	}

	// --- Indicators over a period

	@Test
	void ratiosAreTakenOnTheTotalsNotAveragedHalfHourByHalfHour() {
		IndicatorSeries series = new IndicatorSeries();
		// A sunny half hour: 100 % renewable out of 1000 MW. A night: 0 % out of 3000 MW
		series.add(reading(2000, 0, 0, 0, 1000, 0, 0), null);
		series.add(reading(2000, 3000, 0, 0, 0, 0, 0), null);

		// 1000 / 4000 = 25 %, not the mean of 100 % and 0 %
		assertThat(series.value(TargetIndicator.RENEWABLE_SHARE, null)).hasValue(new BigDecimal("25.00"));
		// 4000 / 4000
		assertThat(series.value(TargetIndicator.COVERAGE_RATE, null)).hasValue(new BigDecimal("100.00"));
		assertThat(series.meanConsumption()).hasValue(new BigDecimal("2000.00"));
	}

	@Test
	void consumptionDropIsAgainstTheMeanOfTheReferenceYear() {
		IndicatorSeries series = new IndicatorSeries();
		series.add(reading(3800, 4000, 0, 0, 0, 0, 0), null);
		series.add(reading(3600, 4000, 0, 0, 0, 0, 0), null);

		// mean 3700 against 4000: 7.5 % less
		assertThat(series.value(TargetIndicator.CONSUMPTION_DROP, new BigDecimal("4000"))).hasValue(new BigDecimal("7.50"));
		// A rise is a negative drop
		assertThat(series.value(TargetIndicator.CONSUMPTION_DROP, new BigDecimal("3500"))).hasValue(new BigDecimal("-5.71"));
	}

	@Test
	void theCo2AverageIsWeightedByProductionOverTheReadingsThatHaveOne() {
		IndicatorSeries series = new IndicatorSeries();
		series.add(reading(1000, 0, 0, 0, 1000, 0, 0), new BigDecimal("0"));
		series.add(reading(1000, 0, 3000, 0, 0, 0, 0), new BigDecimal("429"));
		series.add(reading(1000, 0, 1000, 0, 0, 0, 0), null);

		// (0 x 1000 + 429 x 3000) / 4000, the reading without CO2 left out
		assertThat(series.value(TargetIndicator.ESTIMATED_CO2, null)).hasValue(new BigDecimal("321.75"));
	}

	@Test
	void anIncompleteReadingCountsForNothing() {
		IndicatorSeries series = new IndicatorSeries();
		series.add(EnergyBalance.of(2000, 1000, 0, null, null, null, 100, null, 0, 0), null);
		series.add(EnergyBalance.of(null, 1000, 0, null, null, null, 100, 0, 0, 0), null);

		assertThat(series.periods()).isZero();
		assertThat(series.value(TargetIndicator.RENEWABLE_SHARE, null)).isEmpty();
		assertThat(series.value(TargetIndicator.CONSUMPTION_DROP, new BigDecimal("2000"))).isEmpty();
		assertThat(series.value(TargetIndicator.ESTIMATED_CO2, null)).isEmpty();
	}

	// --- Trajectory and status

	@Test
	void theTrajectoryIsAStraightLineFromCreationToDeadline() {
		Target renewables = target(TargetIndicator.RENEWABLE_SHARE, "40", "60", "2");

		assertThat(TargetAssessment.of(renewables, new BigDecimal("40"), LocalDate.of(2026, 1, 1)).expected())
				.isEqualByComparingTo("40");
		// Half way in time (730 of 1461 days): half way in value
		assertThat(TargetAssessment.of(renewables, new BigDecimal("50"), LocalDate.of(2028, 1, 1)).expected())
				.isEqualByComparingTo("49.99");
		assertThat(TargetAssessment.of(renewables, new BigDecimal("60"), DEADLINE).expected())
				.isEqualByComparingTo("60");
		// After the deadline the target stays the expectation
		assertThat(TargetAssessment.of(renewables, new BigDecimal("60"), LocalDate.of(2031, 6, 1)).expected())
				.isEqualByComparingTo("60");
	}

	@Test
	void theStatusComparesWithTheTrajectoryWithinTheTolerance() {
		Target renewables = target(TargetIndicator.RENEWABLE_SHARE, "40", "60", "2");
		LocalDate halfWay = LocalDate.of(2028, 1, 1);

		assertThat(TargetAssessment.of(renewables, new BigDecimal("53"), halfWay).status()).isEqualTo(TargetStatus.AHEAD);
		assertThat(TargetAssessment.of(renewables, new BigDecimal("51.99"), halfWay).status())
				.isEqualTo(TargetStatus.ON_TRACK);
		assertThat(TargetAssessment.of(renewables, new BigDecimal("48"), halfWay).status())
				.isEqualTo(TargetStatus.ON_TRACK);
		assertThat(TargetAssessment.of(renewables, new BigDecimal("47"), halfWay).status()).isEqualTo(TargetStatus.LATE);
	}

	@Test
	void withoutToleranceOnlyTheExactTrajectoryIsOnTrack() {
		Target renewables = target(TargetIndicator.RENEWABLE_SHARE, "40", "60", null);
		LocalDate halfWay = LocalDate.of(2028, 1, 1);

		assertThat(TargetAssessment.of(renewables, new BigDecimal("49.99"), halfWay).status())
				.isEqualTo(TargetStatus.ON_TRACK);
		assertThat(TargetAssessment.of(renewables, new BigDecimal("49.98"), halfWay).status())
				.isEqualTo(TargetStatus.LATE);
	}

	@Test
	void aTargetToGoDownIsAheadWhenTheValueIsBelowTheTrajectory() {
		// CO2 from 180 to 120: lower is better, read from the target itself
		Target co2 = target(TargetIndicator.ESTIMATED_CO2, "180", "120", "5");
		LocalDate halfWay = LocalDate.of(2028, 1, 1);

		TargetAssessment ahead = TargetAssessment.of(co2, new BigDecimal("140"), halfWay);
		assertThat(ahead.expected()).isEqualByComparingTo("150.02");
		assertThat(ahead.status()).isEqualTo(TargetStatus.AHEAD);
		assertThat(ahead.percent()).isEqualByComparingTo("66.7");
		assertThat(TargetAssessment.of(co2, new BigDecimal("160"), halfWay).status()).isEqualTo(TargetStatus.LATE);
	}

	// --- Progress

	@Test
	void progressIsTheShareOfTheWayCovered() {
		Target renewables = target(TargetIndicator.RENEWABLE_SHARE, "40", "60", null);

		TargetAssessment quarter = TargetAssessment.of(renewables, new BigDecimal("45"), LocalDate.of(2027, 1, 1));
		assertThat(quarter.percent()).isEqualByComparingTo("25.0");
		assertThat(quarter.achieved()).isFalse();
	}

	@Test
	void aReachedTargetIsAchievedAndNeverAboveOneHundredPercent() {
		Target renewables = target(TargetIndicator.RENEWABLE_SHARE, "40", "60", null);

		TargetAssessment beyond = TargetAssessment.of(renewables, new BigDecimal("75"), LocalDate.of(2027, 1, 1));
		assertThat(beyond.achieved()).isTrue();
		assertThat(beyond.percent()).isEqualByComparingTo("100");
		assertThat(TargetAssessment.of(renewables, new BigDecimal("60"), LocalDate.of(2027, 1, 1)).achieved()).isTrue();
	}

	@Test
	void goingTheWrongWayIsZeroPercentNotNegative() {
		Target renewables = target(TargetIndicator.RENEWABLE_SHARE, "40", "60", null);

		TargetAssessment worse = TargetAssessment.of(renewables, new BigDecimal("35"), LocalDate.of(2027, 1, 1));
		assertThat(worse.percent()).isEqualByComparingTo("0");
		assertThat(worse.achieved()).isFalse();
		assertThat(worse.status()).isEqualTo(TargetStatus.LATE);
	}

	@Test
	void aConsumptionDropStartsAtZeroPercentWhateverItsReferenceInMw() {
		// The reference value of a drop is the mean consumption of the reference year, 4000 MW
		Target drop = target(TargetIndicator.CONSUMPTION_DROP, "4000", "10", null);

		assertThat(TargetAssessment.startValue(drop)).isEqualByComparingTo("0");
		TargetAssessment halfDone = TargetAssessment.of(drop, new BigDecimal("5"), LocalDate.of(2028, 1, 1));
		assertThat(halfDone.percent()).isEqualByComparingTo("50.0");
		assertThat(halfDone.expected()).isEqualByComparingTo("5.00");
	}
}
