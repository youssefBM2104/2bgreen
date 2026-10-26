package com.twobgreen.gridintelligence.infrastructure.odre;

import java.math.BigDecimal;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Defensive parsing of ODRÉ measures, whatever their declared type ({@code docs/data/}): a value that is not a clean
 * number is missing, never 0 and never guessed. An unexpected value is logged, so a format change shows up instead
 * of being stored.
 */
final class OdreNumbers {

	private static final Logger log = LoggerFactory.getLogger(OdreNumbers.class);

	/** How ODRÉ writes "no value" besides {@code null} (seen in regional data). Missing, not worth a warning. */
	private static final Set<String> KNOWN_MISSING = Set.of("", "ND", "-");

	private OdreNumbers() {
	}

	/**
	 * A whole number, for MW fields. A decimal is missing rather than rounded: ODRÉ has only ever sent whole MW, so
	 * {@code "49.97"} more likely means a unit change (GW) than a real value, and rounding would hide it.
	 */
	static Integer wholeNumber(String field, String raw) {
		BigDecimal value = decimal(field, raw);
		if (value == null) {
			return null;
		}
		try {
			return value.intValueExact();
		}
		catch (ArithmeticException e) {
			log.warn("ODRÉ {} \"{}\" is not a whole number in the int range, treated as missing", field, raw);
			return null;
		}
	}

	/** Any number, for gCO2eq/kWh. */
	static BigDecimal decimal(String field, String raw) {
		if (raw == null) {
			return null;
		}
		String text = raw.strip();
		if (KNOWN_MISSING.contains(text)) {
			return null;
		}
		try {
			return new BigDecimal(text);
		}
		catch (NumberFormatException e) {
			log.warn("ODRÉ {} \"{}\" is not a number, treated as missing", field, raw);
			return null;
		}
	}
}
