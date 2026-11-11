package com.twobgreen.platform.regional.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import com.twobgreen.platform.advisory.GridScope;
import com.twobgreen.platform.advisory.GridSnapshot;
import com.twobgreen.platform.advisory.GridSnapshots;
import com.twobgreen.platform.advisory.GridUnavailableException;
import com.twobgreen.platform.regional.domain.Regions;

/**
 * Use case of the regional dashboard (U11): the latest reading of the official's region, and the nation at the same
 * time to compare with.
 * <ul>
 * <li>The region comes from the caller's token only, never from the request: an official sees their region and no
 * other.</li>
 * <li>"The same time" is the half hour of the regional reading, from the merged view (K2 ranges are at 30-min grain).
 * The latest national reading would be up to several hours ahead of the region, which publishes later.</li>
 * <li>The regional reading is {@code stale} past {@code maxAge}: regional data comes hours late, so the limit is its
 * own and not the one of the citizen traffic light. A stale reading is still shown, with its time.</li>
 * </ul>
 */
public class GetRegionalDashboard {

	private static final Duration HALF_HOUR = Duration.ofMinutes(30);

	private final GridSnapshots grid;

	private final Clock clock;

	private final Duration maxAge;

	public GetRegionalDashboard(GridSnapshots grid, Clock clock, Duration maxAge) {
		if (maxAge == null || maxAge.isNegative() || maxAge.isZero()) {
			throw new IllegalArgumentException("maxAge is required and must be positive");
		}
		this.grid = grid;
		this.clock = clock;
		this.maxAge = maxAge;
	}

	/**
	 * @param regionCode the {@code region_code} claim of the token, possibly null
	 * @throws RegionNotAssignedException if the code is missing or names no known region
	 * @throws NoRegionalDataException if the region has no reading yet
	 * @throws GridUnavailableException if grid data cannot be read
	 */
	public RegionalDashboard dashboard(String regionCode) {
		String name = Regions.nameOf(regionCode).orElseThrow(RegionNotAssignedException::new);
		GridSnapshot region = grid.latest(new GridScope("region:" + regionCode))
				.orElseThrow(NoRegionalDataException::new);
		Instant halfHour = halfHourOf(region.observedAt());
		GridSnapshot national = grid.between(GridScope.NATIONAL, halfHour, halfHour.plus(HALF_HOUR)).stream()
				.findFirst().orElse(null);
		boolean stale = region.observedAt().plus(maxAge).isBefore(clock.instant());
		return new RegionalDashboard(regionCode, name, region, stale, maxAge, national);
	}

	/** UTC start of the half hour, the grid of the merged view (G4). */
	private static Instant halfHourOf(Instant observedAt) {
		long seconds = HALF_HOUR.toSeconds();
		return Instant.ofEpochSecond(Math.floorDiv(observedAt.getEpochSecond(), seconds) * seconds);
	}
}
