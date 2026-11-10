package com.twobgreen.gridintelligence.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import com.twobgreen.gridintelligence.domain.GridSnapshot;

/**
 * Use case of the regional poller: same guarantees as {@link PollNationalReadings} (the cursor only moves once
 * everything is published: duplicates, never gaps), for all regions at once.
 * <p>
 * One cursor serves every region: the newest reading seen in any of them. Regions are not filled in at the same
 * pace, so the overlap is wider than the national one: a region that is late by less than the overlap is still
 * caught at the next poll. Its pending rows (no measured consumption) are not readings, so they never move the
 * cursor and are asked for again until ODRÉ fills them in.
 */
public class PollRegionalReadings {

	/** What one poll did, for the logs. */
	public record Result(Instant after, int published, Instant lastSeen) {
	}

	private final RegionalOdreGateway odre;

	private final SnapshotPublisher publisher;

	private final SpeedSnapshotRepository stored;

	private final Duration overlap;

	private final Instant firstStart;

	/** Newest reading published; null until the first poll reads it from the stored data. */
	private Instant lastSeen;

	/**
	 * @param overlap how far before the newest reading seen each poll starts again
	 * @param firstStart where to start when no regional reading is stored yet
	 */
	public PollRegionalReadings(RegionalOdreGateway odre, SnapshotPublisher publisher, SpeedSnapshotRepository stored,
			Duration overlap, Instant firstStart) {
		this.odre = odre;
		this.publisher = publisher;
		this.stored = stored;
		this.overlap = overlap;
		this.firstStart = firstStart;
	}

	/**
	 * One poll. On failure the exception goes to the caller and the cursor stays.
	 *
	 * @throws OdreException if ODRÉ cannot be read
	 * @throws RuntimeException if the readings cannot be published
	 */
	public synchronized Result poll() {
		if (lastSeen == null) {
			lastSeen = stored.latestRegionalObservedAt().orElse(firstStart);
		}
		Instant after = lastSeen.minus(overlap);
		List<GridSnapshot> readings = odre.regionalReadingsAfter(after);
		publisher.publish(readings);
		readings.stream()
				.map(GridSnapshot::observedAt)
				.max(Comparator.naturalOrder())
				.filter(newest -> newest.isAfter(lastSeen))
				.ifPresent(newest -> lastSeen = newest);
		return new Result(after, readings.size(), lastSeen);
	}
}
