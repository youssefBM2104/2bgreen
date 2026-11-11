package com.twobgreen.gridintelligence.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Port to read snapshots for the read API (contract K2). {@code latest} comes from the speed layer (the freshest, 15
 * min), ranges from the merged view of speed and batch (30 min).
 */
public interface SnapshotReader {

	/**
	 * The newest snapshot of {@code scope} that has measures. A row that only carries forecasts (no measured
	 * consumption) is not a reading and is never returned, as in contract K1.
	 */
	Optional<StoredSnapshot> latestWithMeasures(String scope);

	/**
	 * The snapshots of {@code scope} whose period starts in {@code [from, to)}, ascending by {@code observedAt}, from
	 * the merged view: for a period the batch row wins, otherwise the provisional quarter-hours combined. The quality
	 * of each is the one of the table it comes from. Missing periods are missing: nothing is filled in.
	 */
	List<StoredSnapshot> range(String scope, Instant from, Instant to);
}
