package com.twobgreen.platform.regional.application;

import java.time.Duration;

import com.twobgreen.platform.advisory.GridSnapshot;

/**
 * What the regional dashboard shows (U11).
 *
 * @param regionCode INSEE code of the official's region
 * @param regionName its name
 * @param region the latest reading of the region
 * @param stale true when that reading is older than {@code maxAge}
 * @param maxAge age above which a regional reading no longer describes the region right now
 * @param national the nation in the half hour of the regional reading, null when that half hour has no data
 */
public record RegionalDashboard(
		String regionCode,
		String regionName,
		GridSnapshot region,
		boolean stale,
		Duration maxAge,
		GridSnapshot national) {
}
