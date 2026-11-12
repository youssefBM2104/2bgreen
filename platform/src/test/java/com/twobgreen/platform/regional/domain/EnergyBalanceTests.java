package com.twobgreen.platform.regional.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/** The balance of the regional dashboard (U11) on real mixes of {@code eco2mix-regional-tr} and K2. */
class EnergyBalanceTests {

	@Test
	void aRegionThatProducesLessThanItConsumesImports() {
		// Provence-Alpes-Côte d'Azur, 2026-11-10 12:00 UTC: 4559 MW consumed, 3742 MW produced
		EnergyBalance paca = EnergyBalance.of(4559, 0, 1417, null, null, null, 25, 556, 1606, 138);

		assertThat(paca.totalProduction()).isEqualTo(3742);
		assertThat(paca.coverageRate()).isEqualByComparingTo("82.1");
		assertThat(paca.direction()).isEqualTo(BalanceDirection.IMPORTING);
		// (25 + 556 + 1606 + 138) / 3742 = 62.1 %
		assertThat(paca.renewableShare()).isEqualByComparingTo("62.1");
	}

	@Test
	void aRegionThatProducesMoreThanItConsumesExports() {
		// Centre-Val de Loire, same time: nuclear, 2363 MW consumed for 8902 produced
		EnergyBalance centre = EnergyBalance.of(2363, 8288, 0, null, null, null, 51, 501, 32, 30);

		assertThat(centre.totalProduction()).isEqualTo(8902);
		assertThat(centre.coverageRate()).isEqualByComparingTo("376.7");
		assertThat(centre.direction()).isEqualTo(BalanceDirection.EXPORTING);
		assertThat(centre.renewableShare()).isEqualByComparingTo("6.9");
	}

	@Test
	void theNationalFossilSplitIsAddedUpLikeTheRegionalAggregate() {
		// Example latest-national.json of K2: gas 2632, coal 0, oil 37, no thermal
		EnergyBalance france = EnergyBalance.of(49970, 38499, null, 2632, 0, 37, 6225, 4656, 3768, 941);

		assertThat(france.thermal()).isEqualTo(2669);
		assertThat(france.totalProduction()).isEqualTo(56758);
		assertThat(france.direction()).isEqualTo(BalanceDirection.EXPORTING);
	}

	@Test
	void theAggregateWinsOverAPartialSplit() {
		EnergyBalance balance = EnergyBalance.of(100, 50, 30, 10, null, null, 10, 0, 0, 0);

		assertThat(balance.thermal()).isEqualTo(30);
	}

	@Test
	void aMissingSourceLeavesTheTotalAndEveryRateUnknown() {
		// Hydro unknown: a total without it would be too small, the rates wrong
		EnergyBalance balance = EnergyBalance.of(3805, 1257, 1, null, null, null, 376, 0, null, 0);

		assertThat(balance.totalProduction()).isNull();
		assertThat(balance.coverageRate()).isNull();
		assertThat(balance.direction()).isNull();
		assertThat(balance.renewableShare()).isNull();
		// What is known is still given
		assertThat(balance.consumption()).isEqualTo(3805);
		assertThat(balance.nuclear()).isEqualTo(1257);
		assertThat(balance.hydro()).isNull();
	}

	@Test
	void aPartialNationalSplitIsNoFossilFigure() {
		EnergyBalance balance = EnergyBalance.of(100, 50, null, 10, null, 0, 10, 0, 0, 0);

		assertThat(balance.thermal()).isNull();
		assertThat(balance.totalProduction()).isNull();
	}

	@Test
	void anUnknownConsumptionLeavesTheCoverageUnknownButNotTheShare() {
		EnergyBalance balance = EnergyBalance.of(null, 0, 100, null, null, null, 100, 0, 0, 0);

		assertThat(balance.coverageRate()).isNull();
		assertThat(balance.direction()).isNull();
		assertThat(balance.renewableShare()).isEqualByComparingTo("50.0");
	}

	@Test
	void producingExactlyWhatIsConsumedIsBalanced() {
		EnergyBalance balance = EnergyBalance.of(200, 100, 0, null, null, null, 100, 0, 0, 0);

		assertThat(balance.direction()).isEqualTo(BalanceDirection.BALANCED);
		assertThat(balance.coverageRate()).isEqualByComparingTo(new BigDecimal("100.0"));
	}

	@Test
	void nothingProducedHasNoShareAndNothingConsumedNoCoverage() {
		EnergyBalance nothing = EnergyBalance.of(0, 0, 0, null, null, null, 0, 0, 0, 0);

		assertThat(nothing.totalProduction()).isZero();
		assertThat(nothing.renewableShare()).isNull();
		assertThat(nothing.coverageRate()).isNull();
		assertThat(nothing.direction()).isEqualTo(BalanceDirection.BALANCED);
	}

	@Test
	void theTwelveRegionsAreKnownByTheirCodeOnly() {
		assertThat(Regions.nameOf("93")).hasValue("Provence-Alpes-Côte d'Azur");
		assertThat(Regions.nameOf("11")).hasValue("Île-de-France");
		assertThat(Regions.nameOf("94")).isEmpty();
		assertThat(Regions.nameOf("Corse")).isEmpty();
		assertThat(Regions.nameOf(null)).isEmpty();
	}
}
