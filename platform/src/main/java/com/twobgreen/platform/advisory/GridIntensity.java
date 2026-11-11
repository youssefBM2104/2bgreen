package com.twobgreen.platform.advisory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * What other modules may read of the grid through {@code advisory}, which owns the client of grid-intelligence (F4).
 * The public API of the DevOps (U7, module {@code automation}) is built on it.
 */
public interface GridIntensity {

	/**
	 * @return the latest reading of the scope and its traffic light, empty if none is stored yet
	 * @throws GridUnavailableException if grid data cannot be read
	 */
	Optional<CurrentIntensity> latest(GridScope scope);

	/**
	 * The readings whose period starts from {@code from} included to {@code to} excluded, ascending, at the 30-minute
	 * grain of the merged view. A period without data is absent. The caller limits the width of the range.
	 *
	 * @throws GridUnavailableException if grid data cannot be read
	 */
	List<IntensityReading> between(GridScope scope, Instant from, Instant to);
}
