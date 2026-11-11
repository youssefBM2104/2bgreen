package com.twobgreen.gridintelligence.application;

import java.util.regex.Pattern;

import com.twobgreen.gridintelligence.domain.GridSnapshot;

/**
 * Turns the region code of a URL into a scope. The code is ODRÉ's {@code code_insee_region} as is (G1): two digits.
 * <p>
 * Only the format is checked, on purpose. There is no verified list of regions (the count of 12 comes from
 * arithmetic), and a list kept here would turn a region not ingested yet into a "platform bug". A well-formed code
 * with no data answers {@code NO_DATA} or an empty range.
 */
final class RegionCodes {

	private static final Pattern INSEE = Pattern.compile("\\d{2}");

	private RegionCodes() {
	}

	/**
	 * @throws UnknownScopeException if {@code code} is not two digits
	 */
	static String scopeOf(String code) {
		if (code == null || !INSEE.matcher(code).matches()) {
			throw new UnknownScopeException(code);
		}
		return GridSnapshot.regionScope(code);
	}
}
