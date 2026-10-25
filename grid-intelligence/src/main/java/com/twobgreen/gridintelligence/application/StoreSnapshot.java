package com.twobgreen.gridintelligence.application;

import com.twobgreen.gridintelligence.domain.GridSnapshot;
import com.twobgreen.gridintelligence.domain.Quality;

/**
 * Use case of the speed layer: save a provisional snapshot.
 */
public class StoreSnapshot {

	private final SpeedSnapshotRepository repository;

	public StoreSnapshot(SpeedSnapshotRepository repository) {
		this.repository = repository;
	}

	/**
	 * @throws IllegalArgumentException if the snapshot is not provisional: the speed table only holds provisional
	 *         readings, consolidated and definitive ones belong to the batch layer
	 */
	public StoredSnapshot store(GridSnapshot snapshot) {
		if (snapshot.quality() != Quality.PROVISIONAL) {
			throw new IllegalArgumentException("speed layer only stores PROVISIONAL snapshots, got " + snapshot.quality());
		}
		return new StoredSnapshot(snapshot, repository.upsert(snapshot));
	}
}
