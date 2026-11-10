package com.twobgreen.platform.automation.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * What the rule engine needs from a stored snapshot (contract K1, topic {@code grid.snapshot-stored}).
 *
 * @param scope {@code national} or {@code region:<code>}
 * @param observedAt UTC start of the period; with the scope, the identity of the snapshot
 * @param co2Intensity gCO2eq/kWh, null when the reading has none
 */
public record GridObservation(String scope, Instant observedAt, BigDecimal co2Intensity) {

	public GridObservation {
		if (scope == null || scope.isBlank() || observedAt == null) {
			throw new IllegalArgumentException("scope and observedAt are required");
		}
	}
}
