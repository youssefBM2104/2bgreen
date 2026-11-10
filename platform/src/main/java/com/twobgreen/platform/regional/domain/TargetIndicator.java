package com.twobgreen.platform.regional.domain;

/** What a target is about (U13). */
public enum TargetIndicator {

	/** Share of renewable production, in %. */
	RENEWABLE_SHARE,

	/** Production over consumption, in %. */
	COVERAGE_RATE,

	/** Drop of consumption against the reference year, in %. */
	CONSUMPTION_DROP,

	/** Average estimated CO2 of the region. Needs the estimation of G7. */
	ESTIMATED_CO2
}
