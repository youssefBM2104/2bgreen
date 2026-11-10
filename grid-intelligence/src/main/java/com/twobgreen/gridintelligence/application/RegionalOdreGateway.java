package com.twobgreen.gridintelligence.application;

import java.time.Instant;
import java.util.List;

import com.twobgreen.gridintelligence.domain.GridSnapshot;

/**
 * Port to the regional provisional readings of ODRÉ. Separate from {@link OdreGateway} (national) because the two
 * datasets are polled at different rhythms and fail independently. Same rule: ODRÉ's fields stay behind the adapter.
 */
public interface RegionalOdreGateway {

	/**
	 * Regional provisional readings, every region, observed strictly after {@code after}, oldest first. Each snapshot
	 * has the scope {@code region:<code>} and no CO2. Rows without measured consumption (periods ODRÉ has not
	 * filled in yet) are not returned.
	 *
	 * @throws OdreException if ODRÉ cannot be read, see {@link OdreException.Reason}
	 */
	List<GridSnapshot> regionalReadingsAfter(Instant after);
}
