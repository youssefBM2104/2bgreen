package com.twobgreen.platform.advisory.domain;

/**
 * What the grid consumes and produces at a reading, in MW (contract K2). Every value is null when missing: a missing
 * value is unknown, never 0.
 * <p>
 * {@code gas}, {@code coal} and {@code oil} are national only; {@code thermal} is the fossil aggregate of regional
 * data, which has no split. {@code wind} and {@code hydro} are totals.
 */
public record GridMix(
		Integer consumption,
		Integer nuclear,
		Integer gas,
		Integer coal,
		Integer oil,
		Integer thermal,
		Integer wind,
		Integer solar,
		Integer hydro,
		Integer bioenergy) {

	/** Nothing known: what a reading carries when its source gives no mix. */
	public static final GridMix NONE = new GridMix(null, null, null, null, null, null, null, null, null, null);
}
