package com.twobgreen.platform.advisory;

/**
 * The latest reading of a scope, with the colour of the citizen traffic light.
 *
 * @param trafficLight {@code GREEN}, {@code AMBER}, {@code RED}, or {@code UNKNOWN} when the CO2 value is missing or
 *        the reading is {@code stale}
 * @param stale true when the reading is too old to describe the grid right now
 */
public record CurrentIntensity(IntensityReading reading, String trafficLight, boolean stale) {
}
