package com.twobgreen.gridintelligence.application;

import java.util.Optional;

/**
 * Port to read snapshots for the read API (contract K2). Today it is backed by the speed layer table; the merged view
 * of speed and batch will replace the adapter without changing this port or the API.
 */
public interface SnapshotReader {

	/**
	 * The newest snapshot of {@code scope} that has measures. A row that only carries forecasts (no measured
	 * consumption) is not a reading and is never returned, as in contract K1.
	 */
	Optional<StoredSnapshot> latestWithMeasures(String scope);
}
