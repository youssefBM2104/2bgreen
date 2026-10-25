package com.twobgreen.gridintelligence.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One reading of the grid for a scope at a given time (contract K1, {@code docs/contracts/events.md}).
 * Identified by ({@code scope}, {@code observedAt}). Every measure is nullable: a missing value is a valid state
 * and means "unknown", never 0.
 *
 * @param scope {@code national} or {@code region:<code>}
 * @param observedAt UTC start of the 15-min period
 * @param co2Intensity gCO2eq/kWh, estimated for regions (see {@code estimated})
 * @param consumption MW
 * @param consumptionForecastDay MW, forecast of consumption (not CO2)
 * @param consumptionForecastDayBefore MW, same forecast issued the day before
 * @param pumping MW drawn from the grid to pump water, zero or negative
 * @param estimated true when the CO2 value is estimated (regional data)
 */
public record GridSnapshot(
		String scope,
		Instant observedAt,
		BigDecimal co2Intensity,
		Integer consumption,
		Integer consumptionForecastDay,
		Integer consumptionForecastDayBefore,
		Production production,
		Integer pumping,
		Quality quality,
		boolean estimated) {

	public GridSnapshot {
		if (scope == null || scope.isBlank()) {
			throw new IllegalArgumentException("scope is required");
		}
		if (observedAt == null) {
			throw new IllegalArgumentException("observedAt is required");
		}
		if (quality == null) {
			throw new IllegalArgumentException("quality is required");
		}
		if (production == null) {
			production = Production.NONE;
		}
	}
}
