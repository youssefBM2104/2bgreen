package com.twobgreen.platform.automation.domain;

import java.math.BigDecimal;

/** When a webhook fires (U9). */
public enum WebhookCondition {

	/** CO2 goes below the threshold. A value equal to the threshold is not below it. */
	BELOW,

	/** CO2 goes above the threshold. A value equal to the threshold is not above it. */
	ABOVE,

	/** The traffic-light colour changes. No threshold. Not evaluated on snapshots: it waits for the event of G9. */
	BAND_CHANGED;

	/** Is this threshold condition true for that reading? Never for {@link #BAND_CHANGED}. */
	public boolean isMet(BigDecimal co2Intensity, Integer threshold) {
		if (co2Intensity == null || threshold == null) {
			return false;
		}
		int side = co2Intensity.compareTo(BigDecimal.valueOf(threshold));
		return switch (this) {
			case BELOW -> side < 0;
			case ABOVE -> side > 0;
			case BAND_CHANGED -> false;
		};
	}
}
