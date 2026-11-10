package com.twobgreen.platform.advisory.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The state of the grid as a traffic light.
 *
 * @param level the colour, {@code UNKNOWN} when {@code stale} or when the CO2 value is missing
 * @param co2Intensity gCO2eq/kWh, null when missing
 * @param observedAt UTC start of the period, gives the freshness of the data
 * @param stale true when the reading is too old to describe the grid right now
 */
public record TrafficLight(TrafficLightLevel level, BigDecimal co2Intensity, Instant observedAt, boolean stale) {
}
