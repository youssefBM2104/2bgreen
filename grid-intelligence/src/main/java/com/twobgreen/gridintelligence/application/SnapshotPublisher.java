package com.twobgreen.gridintelligence.application;

import java.util.List;

import com.twobgreen.gridintelligence.domain.GridSnapshot;

/**
 * Port to the event backbone: announces new provisional readings ({@code grid.snapshot-received}, contract K1).
 */
public interface SnapshotPublisher {

	/**
	 * Publishes the snapshots in order, one message each, and returns once all of them are acknowledged.
	 *
	 * @throws RuntimeException if any of them may not have been published; some may have been, so the caller must
	 *         publish the whole list again, which consumers absorb because they are idempotent
	 */
	void publish(List<GridSnapshot> snapshots);
}
