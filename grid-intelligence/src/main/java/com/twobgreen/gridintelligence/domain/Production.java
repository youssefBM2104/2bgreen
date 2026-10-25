package com.twobgreen.gridintelligence.domain;

/**
 * Production by source, in MW. A source that is not available is {@code null}, never 0.
 * {@code gas}, {@code coal} and {@code oil} are national only; {@code thermal} is the fossil aggregate for sources
 * that do not split it (regional data). {@code wind} and {@code hydro} are totals.
 */
public record Production(
		Integer nuclear,
		Integer gas,
		Integer coal,
		Integer oil,
		Integer thermal,
		Integer wind,
		Integer solar,
		Integer hydro,
		Integer bioenergy) {

	public static final Production NONE = new Production(null, null, null, null, null, null, null, null, null);
}
