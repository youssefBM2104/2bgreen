package com.twobgreen.platform.advisory.domain;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 * Turns a reading into a traffic light. A value equal to a limit belongs to the worse colour.
 *
 * @param greenBelow gCO2eq/kWh, below this the light is green
 * @param amberBelow gCO2eq/kWh, below this the light is amber, from this value it is red
 * @param maxAge how old a reading can be and still describe the grid right now; a reading of exactly this age is
 *        still current
 */
public record TrafficLightPolicy(BigDecimal greenBelow, BigDecimal amberBelow, Duration maxAge) {

	public TrafficLightPolicy {
		if (greenBelow == null || amberBelow == null || greenBelow.compareTo(amberBelow) >= 0) {
			throw new IllegalArgumentException("greenBelow and amberBelow are required, greenBelow below amberBelow");
		}
		if (maxAge == null || maxAge.isNegative() || maxAge.isZero()) {
			throw new IllegalArgumentException("maxAge is required and must be positive");
		}
	}

	public TrafficLight evaluate(GridReading reading, Instant now) {
		boolean stale = Duration.between(reading.observedAt(), now).compareTo(maxAge) > 0;
		TrafficLightLevel level = stale ? TrafficLightLevel.UNKNOWN : levelOf(reading.co2Intensity());
		return new TrafficLight(level, reading.co2Intensity(), reading.observedAt(), stale);
	}

	private TrafficLightLevel levelOf(BigDecimal co2Intensity) {
		if (co2Intensity == null) {
			return TrafficLightLevel.UNKNOWN;
		}
		if (co2Intensity.compareTo(greenBelow) < 0) {
			return TrafficLightLevel.GREEN;
		}
		return co2Intensity.compareTo(amberBelow) < 0 ? TrafficLightLevel.AMBER : TrafficLightLevel.RED;
	}
}
