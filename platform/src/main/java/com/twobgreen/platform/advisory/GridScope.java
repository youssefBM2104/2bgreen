package com.twobgreen.platform.advisory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which part of the grid a reading describes: {@code national} or {@code region:<code>}, the same strings as the
 * Kafka key and the {@code scope} field of contracts K1 and K2. The region code is the two-digit INSEE code, and only
 * its format is checked, as K2 does: a well-formed code without data simply has no reading.
 */
public record GridScope(String value) {

	private static final String NATIONAL_VALUE = "national";

	private static final Pattern REGION = Pattern.compile("region:(\\d{2})");

	public static final GridScope NATIONAL = new GridScope(NATIONAL_VALUE);

	/**
	 * @throws IllegalArgumentException if {@code value} is neither {@code national} nor {@code region:<two digits>}
	 */
	public GridScope {
		if (value == null || !(NATIONAL_VALUE.equals(value) || REGION.matcher(value).matches())) {
			throw new IllegalArgumentException("scope must be 'national' or 'region:<two-digit code>'");
		}
	}

	public boolean isNational() {
		return NATIONAL_VALUE.equals(value);
	}

	/** The INSEE code of a regional scope. */
	public String regionCode() {
		Matcher matcher = REGION.matcher(value);
		if (!matcher.matches()) {
			throw new IllegalStateException("national scope has no region code");
		}
		return matcher.group(1);
	}

	@Override
	public String toString() {
		return value;
	}
}
