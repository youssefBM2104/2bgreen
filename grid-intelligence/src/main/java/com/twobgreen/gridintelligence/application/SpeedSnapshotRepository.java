package com.twobgreen.gridintelligence.application;

import java.time.Instant;

import com.twobgreen.gridintelligence.domain.GridSnapshot;

/**
 * Port to the speed layer table. The stream is its only writer.
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
}
