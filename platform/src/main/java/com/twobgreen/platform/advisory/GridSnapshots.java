package com.twobgreen.platform.advisory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The whole readings of the grid (consumption, production by source, CO2), for the modules that show more than an
 * intensity. Read through {@code advisory}, which owns the client of grid-intelligence (F4), like
 * {@link GridIntensity}. The regional dashboard (U11, module {@code regional}) is built on it.
 */
public interface GridSnapshots {

	/**
	 * @return the latest snapshot of the scope (15-min grain), empty if none is stored yet
	 * @throws GridUnavailableException if grid data cannot be read
	 */
	Optional<GridSnapshot> latest(GridScope scope);

	/**
	 * The snapshots whose period starts from {@code from} included to {@code to} excluded, ascending, at the
	 * 30-minute grain of the merged view. A period without data is absent. The caller limits the width of the range.
	 *
	 * @throws GridUnavailableException if grid data cannot be read
	 */
	List<GridSnapshot> between(GridScope scope, Instant from, Instant to);
}
