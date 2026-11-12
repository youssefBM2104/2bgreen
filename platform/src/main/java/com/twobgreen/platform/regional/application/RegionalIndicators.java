package com.twobgreen.platform.regional.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import com.twobgreen.platform.advisory.GridScope;
import com.twobgreen.platform.advisory.GridSnapshot;
import com.twobgreen.platform.advisory.GridSnapshots;
import com.twobgreen.platform.advisory.GridUnavailableException;
import com.twobgreen.platform.regional.domain.EnergyBalance;
import com.twobgreen.platform.regional.domain.IndicatorSeries;

/**
 * The readings of a region over a period, from the read API of grid-intelligence (K2 range, 30-min grain of the merged
 * view: consolidated and definitive history from G6, provisional after). One call per period, at most 366 days (K2).
 * <p>
 * A period with too few complete readings has no value: an average over a few months would pass for a yearly one.
 */
public class RegionalIndicators {

	private static final Duration HALF_HOUR = Duration.ofMinutes(30);

	private final GridSnapshots grid;

	private final double minCoverage;

	/**
	 * @param minCoverage share of the half hours of the period that must have a complete reading, 0 to 1
	 */
	public RegionalIndicators(GridSnapshots grid, double minCoverage) {
		if (minCoverage <= 0 || minCoverage > 1) {
			throw new IllegalArgumentException("minCoverage must be in ]0, 1]");
		}
		this.grid = grid;
		this.minCoverage = minCoverage;
	}

	/**
	 * @return the readings of {@code [from, to)}, empty when fewer than {@code minCoverage} of its half hours have a
	 *         complete reading
	 * @throws GridUnavailableException if grid data cannot be read
	 */
	public Optional<IndicatorSeries> over(String regionCode, Instant from, Instant to) {
		IndicatorSeries series = new IndicatorSeries();
		for (GridSnapshot snapshot : grid.between(new GridScope("region:" + regionCode), from, to)) {
			GridSnapshot.Production p = snapshot.production();
			series.add(EnergyBalance.of(snapshot.consumption(), p.nuclear(), p.thermal(), p.gas(), p.coal(), p.oil(),
					p.wind(), p.solar(), p.hydro(), p.bioenergy()), snapshot.co2Intensity());
		}
		long expected = Duration.between(from, to).dividedBy(HALF_HOUR);
		return series.periods() >= Math.ceil(expected * minCoverage) ? Optional.of(series) : Optional.empty();
	}
}
