package com.twobgreen.gridintelligence.application;

import java.time.Instant;
import java.util.Optional;

import com.twobgreen.gridintelligence.domain.GridSnapshot;

/**
 * Port to the speed layer table. The stream is its only writer; the poller only reads it.
 */
public interface SpeedSnapshotRepository {

	/**
	 * Inserts the snapshot, or replaces the existing row with the same ({@code scope}, {@code observedAt}).
	 * Idempotent: the same snapshot twice gives the same state. The latest snapshot wins, so a field that is
	 * now missing becomes missing in the stored row.
	 *
	 * @return the time the row was written
	 */
	Instant upsert(GridSnapshot snapshot);

	/**
	 * When the newest stored reading of {@code scope} was observed. Read by the poller at startup to resume where the
	 * stored data ends, so a restart re-sends (duplicates are absorbed) rather than skips.
	 */
	Optional<Instant> latestObservedAt(String scope);
}
