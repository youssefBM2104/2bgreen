package com.twobgreen.gridintelligence.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Estimates the CO2 intensity of a region from its production mix (G7): regional data has no {@code taux_co2}.
 * Method and limits: {@code docs/data/regional-co2.md}.
 * <p>
 * Same definition as RTE's national value, so that a regional figure and a national one are the same quantity: direct
 * emissions of combustion divided by the total production of the scope, imports and exports not counted. It tells
 * what a region produces, not what it consumes.
 * <ul>
 * <li>Only thermal and bioenergy emit. Fossil production comes as one aggregate in regional data, so it gets the
 * factor of gas, which is 97 % of French fossil production (Sept. to Oct. 2026). Coal and oil are underestimated when
 * they run.</li>
 * <li>Every source must be known: a missing one would change the total and give a wrong value, so there is no
 * estimate at all. A missing value stays missing, never 0.</li>
 * <li>Rounded to a whole number, like RTE's national value.</li>
 * </ul>
 */
public final class RegionalCo2Estimator {

	/** gCO2eq/kWh of fossil production: RTE's factor for gas. */
	static final BigDecimal THERMAL_FACTOR = BigDecimal.valueOf(429);

	/** gCO2eq/kWh of bioenergy: RTE's factor. */
	static final BigDecimal BIOENERGY_FACTOR = BigDecimal.valueOf(494);

	/**
	 * The snapshot with its estimated CO2 and {@code estimated = true}. Unchanged when it is not regional, when it
	 * already has a CO2 value, or when its mix is not complete enough to estimate one.
	 */
	public GridSnapshot estimate(GridSnapshot snapshot) {
		if (!snapshot.scope().startsWith(GridSnapshot.REGION_SCOPE_PREFIX) || snapshot.co2Intensity() != null) {
			return snapshot;
		}
		return intensityOf(snapshot.production())
				.map(co2 -> new GridSnapshot(snapshot.scope(), snapshot.observedAt(), co2, snapshot.consumption(),
						snapshot.consumptionForecastDay(), snapshot.consumptionForecastDayBefore(),
						snapshot.production(), snapshot.pumping(), snapshot.quality(), true))
				.orElse(snapshot);
	}

	/**
	 * gCO2eq/kWh of a regional mix (fossil in {@code thermal}), empty when a source is missing or nothing is produced.
	 * Gas, coal and oil are not read: regional data never has them.
	 */
	public Optional<BigDecimal> intensityOf(Production production) {
		Integer[] sources = { production.nuclear(), production.thermal(), production.wind(), production.solar(),
				production.hydro(), production.bioenergy() };
		long total = 0;
		for (Integer source : sources) {
			if (source == null) {
				return Optional.empty();
			}
			total += source;
		}
		if (total <= 0) {
			return Optional.empty();
		}
		BigDecimal emissions = THERMAL_FACTOR.multiply(BigDecimal.valueOf(production.thermal()))
				.add(BIOENERGY_FACTOR.multiply(BigDecimal.valueOf(production.bioenergy())));
		return Optional.of(emissions.divide(BigDecimal.valueOf(total), 0, RoundingMode.HALF_UP));
	}
}
