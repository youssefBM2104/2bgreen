package com.twobgreen.platform.advisory;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One whole reading of the grid, as other modules receive it (contract K2). Every measure is null when missing: a
 * missing value is unknown, never 0.
 *
 * @param observedAt UTC start of the period
 * @param consumption MW
 * @param co2Intensity gCO2eq/kWh; for a region, an estimate from its production mix (G7)
 * @param estimated true exactly when {@code co2Intensity} is an estimate
 * @param quality {@code PROVISIONAL}, {@code CONSOLIDATED} or {@code DEFINITIVE}
 * @param storedAt UTC time the reading was saved, gives the freshness of the data
 */
public record GridSnapshot(
		GridScope scope,
		Instant observedAt,
		Integer consumption,
		Production production,
		BigDecimal co2Intensity,
		boolean estimated,
		String quality,
		Instant storedAt) {

	/**
	 * Production by source, MW. {@code gas}, {@code coal} and {@code oil} are national only; {@code thermal} is the
	 * fossil aggregate of regional data, which has no split.
	 */
	public record Production(
			Integer nuclear,
			Integer gas,
			Integer coal,
			Integer oil,
			Integer thermal,
			Integer wind,
			Integer solar,
			Integer hydro,
			Integer bioenergy) {
	}
}
