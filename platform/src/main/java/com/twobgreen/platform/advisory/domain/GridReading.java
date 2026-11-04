package com.twobgreen.platform.advisory.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The state of the grid as this module sees it. Only carries what its features use.
 *
 * @param scope {@code national} or {@code region:<code>}
 * @param observedAt UTC start of the period
 * @param co2Intensity gCO2eq/kWh, null when missing: a missing value is unknown, never 0
 * @param estimated true when the CO2 value is estimated
 * @param storedAt UTC time the reading was saved, gives the freshness of the data
 */
public record GridReading(
		String scope,
		Instant observedAt,
		BigDecimal co2Intensity,
		GridQuality quality,
		boolean estimated,
		Instant storedAt) {
}
