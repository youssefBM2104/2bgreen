package com.twobgreen.gridintelligence.application;

import java.time.Instant;
import java.util.List;

import com.twobgreen.gridintelligence.domain.GridSnapshot;

/**
 * Port to ODRÉ, the source of grid readings. The adapter keeps every ODRÉ field name and DTO to itself
 * (anti-corruption layer): callers only see domain snapshots.
 */
public interface OdreGateway {

	/**
	 * National provisional readings observed strictly after {@code after}, oldest first. Rows without measures
	 * (forecast tail) are not returned.
	 *
	 * @throws OdreException if ODRÉ cannot be read, see {@link OdreException.Reason}
	 */
	List<GridSnapshot> nationalReadingsAfter(Instant after);
}
