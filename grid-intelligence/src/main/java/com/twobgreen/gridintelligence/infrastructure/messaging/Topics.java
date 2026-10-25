package com.twobgreen.gridintelligence.infrastructure.messaging;

/**
 * Kafka topic names, fixed by contract K1 ({@code docs/contracts/events.md}).
 */
public final class Topics {

	/** Raw provisional readings: produced by the poller, consumed by the stream. */
	public static final String SNAPSHOT_RECEIVED = "grid.snapshot-received";

	/** Readings saved in {@code speed_snapshot}: produced by the stream, consumed by {@code platform}. */
	public static final String SNAPSHOT_STORED = "grid.snapshot-stored";

	private Topics() {
	}
}
