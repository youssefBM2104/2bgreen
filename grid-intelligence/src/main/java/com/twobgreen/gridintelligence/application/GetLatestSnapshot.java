package com.twobgreen.gridintelligence.application;

/**
 * Use case of the read API: the latest snapshot of a scope.
 */
public class GetLatestSnapshot {

	private final SnapshotReader reader;

	public GetLatestSnapshot(SnapshotReader reader) {
		this.reader = reader;
	}

	/**
	 * @throws NoSnapshotException if nothing with measures is stored for {@code scope}
	 */
	public StoredSnapshot latest(String scope) {
		return reader.latestWithMeasures(scope).orElseThrow(() -> new NoSnapshotException(scope));
	}
}
