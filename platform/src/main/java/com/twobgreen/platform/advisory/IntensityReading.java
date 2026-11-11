package com.twobgreen.platform.advisory;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One CO2 reading of the grid, as other modules receive it.
 *
 * @param observedAt UTC start of the period
 * @param co2Intensity gCO2eq/kWh, null when missing: a missing value is unknown, never 0
 * @param quality {@code PROVISIONAL}, {@code CONSOLIDATED} or {@code DEFINITIVE} (contract K2)
 * @param estimated true when the CO2 value is estimated (regional data)
 * @param storedAt UTC time the reading was saved, gives the freshness of the data
 */
public record IntensityReading(
		GridScope scope,
		Instant observedAt,
		BigDecimal co2Intensity,
		String quality,
		boolean estimated,
		Instant storedAt) {
}
