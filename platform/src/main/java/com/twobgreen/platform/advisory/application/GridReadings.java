package com.twobgreen.platform.advisory.application;

import java.util.Optional;

import com.twobgreen.platform.advisory.domain.GridReading;

/** Port: where this module gets grid data, without knowing the source. */
public interface GridReadings {

	/**
	 * @return the latest national reading, empty if none is stored yet (normal on a fresh start)
	 * @throws GridUnavailableException if the source cannot answer
	 */
	Optional<GridReading> latestNational();
}
