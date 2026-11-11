package com.twobgreen.gridintelligence.infrastructure.messaging;

/**
 * Kafka topic names, fixed by contract K1 ({@code docs/contracts/events.md}).
 */
public final class Topics {

	/** Raw provisional readings: produced by the poller, consumed by the stream. */
	public static final String SNAPSHOT_RECEIVED = "grid.snapshot-received";

	/** Readings saved in {@code speed_snapshot}: produced by the stream, consumed by {@code platform}. */
	public static final String SNAPSHOT_STORED = "grid.snapshot-stored";

	/** The shared CO2 band of a scope changed: produced by the stream (G9), no consumer yet (U9). */
	public static final String BAND_CHANGED = "grid.band-changed";

	private Topics() {
	}
}
