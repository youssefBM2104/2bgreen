package com.twobgreen.gridintelligence.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** A {@link SnapshotReader} for the tests of the use cases: what it answers is set by the test, what it is asked is kept. */
final class StubSnapshotReader implements SnapshotReader {

	private final Function<String, Optional<StoredSnapshot>> latest;

	private final List<StoredSnapshot> items;

	/** Every call of {@link #range}, as {@code scope|from|to}. */
	final List<String> rangeCalls = new ArrayList<>();

	private StubSnapshotReader(Function<String, Optional<StoredSnapshot>> latest, List<StoredSnapshot> items) {
		this.latest = latest;
		this.items = items;
	}

	static StubSnapshotReader latest(Function<String, Optional<StoredSnapshot>> latest) {
		return new StubSnapshotReader(latest, List.of());
	}

	static StubSnapshotReader range(List<StoredSnapshot> items) {
		return new StubSnapshotReader(scope -> Optional.empty(), items);
	}

	@Override
	public Optional<StoredSnapshot> latestWithMeasures(String scope) {
		return latest.apply(scope);
	}

	@Override
	public List<StoredSnapshot> range(String scope, Instant from, Instant to) {
		rangeCalls.add(scope + "|" + from + "|" + to);
		return items;
	}
}
