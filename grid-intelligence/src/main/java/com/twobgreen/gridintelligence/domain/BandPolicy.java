package com.twobgreen.gridintelligence.domain;

import java.math.BigDecimal;

/**
 * Turns a CO2 intensity into a band. A value equal to a limit belongs to the worse band.
 * <p>
 * Same rule and same limits as the traffic light of {@code platform} ({@code advisory.traffic-light.*}, F5), which
 * computes its own colour for display. The two copies of the limits are not shared code: keep them equal.
 *
 * @param greenBelow gCO2eq/kWh, below this the band is green
 * @param amberBelow gCO2eq/kWh, below this the band is amber, from this value it is red
 */
public record BandPolicy(BigDecimal greenBelow, BigDecimal amberBelow) {

	public BandPolicy {
		if (greenBelow == null || amberBelow == null || greenBelow.compareTo(amberBelow) >= 0) {
			throw new IllegalArgumentException("greenBelow and amberBelow are required, greenBelow below amberBelow");
		}
	}

	public Band bandOf(BigDecimal co2Intensity) {
		if (co2Intensity.compareTo(greenBelow) < 0) {
			return Band.GREEN;
		}
		return co2Intensity.compareTo(amberBelow) < 0 ? Band.AMBER : Band.RED;
	}
}
