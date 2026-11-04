package com.twobgreen.platform.advisory.domain;

/** The colour shown to the citizen. {@code UNKNOWN} lights nothing: no CO2 value, or a value too old to be current. */
public enum TrafficLightLevel {
	GREEN,
	AMBER,
	RED,
	UNKNOWN
}
