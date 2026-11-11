package com.twobgreen.gridintelligence.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

/** The regional CO2 estimate (G7) on known mixes. Method: {@code docs/data/regional-co2.md}. */
class RegionalCo2EstimatorTests {

	private static final Instant T = Instant.parse("2026-10-20T14:45:00Z");

	/** Hauts-de-France, fixture {@code regional-tr-measured.json}. */
	private static final Production HAUTS_DE_FRANCE = new Production(3562, null, null, null, 1173, 1374, 50, 3, 79);

	private final RegionalCo2Estimator estimator = new RegionalCo2Estimator();

	@Test
	void knownRegionalMix() {
		// (429 x 1173 + 494 x 79) / (3562 + 1173 + 1374 + 50 + 3 + 79) = 542243 / 6241 = 86.88
		assertThat(estimator.intensityOf(HAUTS_DE_FRANCE)).hasValue(BigDecimal.valueOf(87));
	}

	@Test
	void liveSamplesOfTwoOppositeRegions() {
		// eco2mix-regional-tr, 2026-11-10 12:00 UTC. Île-de-France produces little, mostly thermal and bioenergy
		Production ileDeFrance = new Production(0, null, null, null, 760, 6, 41, 18, 159);
		// Centre-Val de Loire: nuclear, a little bioenergy
		Production centre = new Production(8288, null, null, null, 0, 51, 501, 32, 30);

		assertThat(estimator.intensityOf(ileDeFrance)).hasValue(BigDecimal.valueOf(411));
		assertThat(estimator.intensityOf(centre)).hasValue(BigDecimal.valueOf(2));
	}

	@Test
	void appliedToTheNationalMixItFindsRtesOwnValue() {
		// Fixture national-tr-measured.json, where RTE gives taux_co2 = 29. Fossil put together as regional data does:
		// gas 2632 + coal 0 + oil 37 = 2669. The estimate is 28.36, rounded to 28: off by the rounding only
		Production national = new Production(38499, null, null, null, 2669, 6225, 4656, 3768, 941);

		assertThat(estimator.intensityOf(national).orElseThrow().doubleValue()).isCloseTo(29, within(1.0));
	}

	@Test
	void noEmittingSourceGivesZero() {
		Production clean = new Production(5000, null, null, null, 0, 300, 200, 100, 0);

		assertThat(estimator.intensityOf(clean)).hasValue(BigDecimal.ZERO);
	}

	@Test
	void aMissingSourceGivesNoEstimateRatherThanAWrongOne() {
		// Hydro unknown: the total would be too small and the intensity too high
		Production hydroMissing = new Production(3562, null, null, null, 1173, 1374, 50, null, 79);
		Production thermalMissing = new Production(3562, null, null, null, null, 1374, 50, 3, 79);

		assertThat(estimator.intensityOf(hydroMissing)).isEmpty();
		assertThat(estimator.intensityOf(thermalMissing)).isEmpty();
		assertThat(estimator.intensityOf(Production.NONE)).isEmpty();
	}

	@Test
	void nothingProducedGivesNoEstimate() {
		assertThat(estimator.intensityOf(new Production(0, null, null, null, 0, 0, 0, 0, 0))).isEmpty();
	}

	@Test
	void aRegionalSnapshotGetsItsEstimateMarkedEstimated() {
		GridSnapshot reading = regional(HAUTS_DE_FRANCE);

		GridSnapshot estimated = estimator.estimate(reading);

		assertThat(estimated.co2Intensity()).isEqualTo(BigDecimal.valueOf(87));
		assertThat(estimated.estimated()).isTrue();
		assertThat(estimated).usingRecursiveComparison().ignoringFields("co2Intensity", "estimated").isEqualTo(reading);
	}

	@Test
	void aRegionalSnapshotWithoutACompleteMixStaysWithoutCo2AndNotEstimated() {
		GridSnapshot reading = regional(new Production(3562, null, null, null, 1173, 1374, 50, null, 79));

		assertThat(estimator.estimate(reading)).isEqualTo(reading);
	}

	@Test
	void aNationalSnapshotIsNeverEstimated() {
		GridSnapshot measured = new GridSnapshot(GridSnapshot.NATIONAL_SCOPE, T, BigDecimal.valueOf(29), 49970, null,
				null, HAUTS_DE_FRANCE, 0, Quality.PROVISIONAL, false);
		GridSnapshot withoutCo2 = new GridSnapshot(GridSnapshot.NATIONAL_SCOPE, T, null, 49970, null, null,
				HAUTS_DE_FRANCE, 0, Quality.PROVISIONAL, false);

		assertThat(estimator.estimate(measured)).isEqualTo(measured);
		assertThat(estimator.estimate(withoutCo2)).isEqualTo(withoutCo2);
	}

	@Test
	void aCo2ValueAlreadyThereIsKept() {
		GridSnapshot alreadyEstimated = new GridSnapshot("region:32", T, BigDecimal.valueOf(90), 5539, null, null,
				HAUTS_DE_FRANCE, 0, Quality.PROVISIONAL, true);

		assertThat(estimator.estimate(alreadyEstimated)).isEqualTo(alreadyEstimated);
	}

	private static GridSnapshot regional(Production production) {
		return new GridSnapshot("region:32", T, null, 5539, null, null, production, 0, Quality.PROVISIONAL, false);
	}
}
