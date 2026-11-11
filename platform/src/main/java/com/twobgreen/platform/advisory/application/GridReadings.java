package com.twobgreen.platform.advisory.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.twobgreen.platform.advisory.GridScope;
import com.twobgreen.platform.advisory.GridUnavailableException;
import com.twobgreen.platform.advisory.domain.GridReading;

/** Port: where this module gets grid data, without knowing the source. */
public interface GridReadings {

	/**
	 * @return the latest reading of the scope, empty if none is stored yet (normal on a fresh start)
	 * @throws GridUnavailableException if the source cannot answer
	 */
	Optional<GridReading> latest(GridScope scope);

	/**
	 * @return the readings whose period starts from {@code from} included to {@code to} excluded, ascending
	 * @throws GridUnavailableException if the source cannot answer
	 */
	List<GridReading> between(GridScope scope, Instant from, Instant to);

	/**
	 * @return the latest national reading, empty if none is stored yet
	 * @throws GridUnavailableException if the source cannot answer
	 */
	default Optional<GridReading> latestNational() {
		return latest(GridScope.NATIONAL);
	}
}
