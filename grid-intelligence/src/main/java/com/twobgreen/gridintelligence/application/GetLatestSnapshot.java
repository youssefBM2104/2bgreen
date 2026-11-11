package com.twobgreen.gridintelligence.application;

/**
 * Use case of the read API: the latest snapshot of a scope (national, or a region by its INSEE code).
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

	/**
	 * @throws UnknownScopeException if {@code code} is not an INSEE region code
	 * @throws NoSnapshotException if nothing with measures is stored for that region yet
	 */
	public StoredSnapshot latestOfRegion(String code) {
		return latest(RegionCodes.scopeOf(code));
	}
}
