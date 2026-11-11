package com.twobgreen.gridintelligence.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The band of a scope changed (event {@code grid.band-changed}, contract K1).
 *
 * @param band the new band
 * @param previousBand the band before, {@code null} for the first snapshot ever seen for the scope
 * @param observedAt the snapshot that changed the band; with the scope, the identity of the event
 * @param co2Intensity gCO2eq/kWh of that snapshot
 */
public record BandChange(String scope, Band band, Band previousBand, Instant observedAt, BigDecimal co2Intensity) {

	public BandChange {
		if (scope == null || scope.isBlank() || band == null || observedAt == null || co2Intensity == null) {
			throw new IllegalArgumentException("scope, band, observedAt and co2Intensity are required");
		}
	}
}
