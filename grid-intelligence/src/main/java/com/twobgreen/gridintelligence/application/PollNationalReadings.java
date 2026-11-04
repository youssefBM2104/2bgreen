package com.twobgreen.gridintelligence.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import com.twobgreen.gridintelligence.domain.GridSnapshot;

/**
 * Use case of the poller: fetch the national readings ODRÉ published since the last poll and announce them.
 * <p>
 * Nothing is lost and nothing is invented: the cursor (newest reading published) only moves once every reading of
 * the poll is published, so a failure makes the next poll ask for the same range again. Re-sending is harmless,
 * the stream upserts on ({@code scope}, {@code observedAt}). The price of that guarantee is duplicates, never gaps.
 */
public class PollNationalReadings {

	/** What one poll did, for the logs. */
	public record Result(Instant after, int published, Instant lastSeen) {
	}

	private final OdreGateway odre;

	private final SnapshotPublisher publisher;

	private final SpeedSnapshotRepository stored;

	private final Duration overlap;

	private final Instant firstStart;

	/** Newest reading published; null until the first poll reads it from the stored data. */
	private Instant lastSeen;

	/**
	 * @param overlap how far before the last reading seen each poll starts again: catches rows ODRÉ publishes late or
	 *        revises in place, at the cost of a few duplicates
	 * @param firstStart where to start when nothing is stored yet (empty database)
	 */
	public PollNationalReadings(OdreGateway odre, SnapshotPublisher publisher, SpeedSnapshotRepository stored,
			Duration overlap, Instant firstStart) {
		this.odre = odre;
		this.publisher = publisher;
		this.stored = stored;
		this.overlap = overlap;
		this.firstStart = firstStart;
	}

	/**
	 * One poll: exactly one gateway call. On failure the exception goes to the caller and the cursor stays.
	 *
	 * @throws OdreException if ODRÉ cannot be read
	 * @throws RuntimeException if the readings cannot be published
	 */
	public synchronized Result poll() {
		Instant after = cursor().minus(overlap);
		List<GridSnapshot> readings = odre.nationalReadingsAfter(after);
		publisher.publish(readings);
		// The overlap brings back older readings: the cursor never moves backwards
		readings.stream()
				.map(GridSnapshot::observedAt)
				.max(Comparator.naturalOrder())
				.filter(newest -> newest.isAfter(lastSeen))
				.ifPresent(newest -> lastSeen = newest);
		return new Result(after, readings.size(), lastSeen);
	}

	/**
	 * After a restart, resume where the stored data ends: anything published but not stored yet is sent again rather
	 * than skipped.
	 */
	private Instant cursor() {
		if (lastSeen == null) {
			lastSeen = stored.latestObservedAt(GridSnapshot.NATIONAL_SCOPE).orElse(firstStart);
		}
		return lastSeen;
	}
}
