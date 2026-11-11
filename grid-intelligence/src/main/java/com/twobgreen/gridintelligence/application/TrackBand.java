package com.twobgreen.gridintelligence.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import com.twobgreen.gridintelligence.domain.Band;
import com.twobgreen.gridintelligence.domain.BandChange;
import com.twobgreen.gridintelligence.domain.BandPolicy;
import com.twobgreen.gridintelligence.domain.BandState;

/**
 * Use case of the stream (G9): follow the band of a scope from one stored snapshot to the next, and tell when it
 * changes. Pure: the state comes in and goes out, the stream keeps it in a state store.
 * <ul>
 * <li>The first snapshot of a scope changes the band (from nothing to something): it is announced.</li>
 * <li>A snapshot in the same band changes nothing, but moves the state forward so the next one is compared with it.</li>
 * <li>A snapshot that is not newer than the last one seen is ignored: a replay (Kafka delivers at least once) or a
 * late arrival. It must not move the state back.</li>
 * <li>A snapshot without CO2 has no band. It is ignored too, and does not count as "seen".</li>
 * </ul>
 */
public class TrackBand {

	/**
	 * @param state what to store for the scope; the previous one, unchanged, when the snapshot was ignored
	 * @param change present when the band changed
	 */
	public record Outcome(BandState state, Optional<BandChange> change) {
	}

	private final BandPolicy policy;

	public TrackBand(BandPolicy policy) {
		this.policy = policy;
	}

	/**
	 * @param previous the state stored for the scope, {@code null} if the scope was never seen
	 * @param co2Intensity {@code null} when the snapshot has none
	 */
	public Outcome observe(String scope, BandState previous, Instant observedAt, BigDecimal co2Intensity) {
		if (co2Intensity == null) {
			return new Outcome(previous, Optional.empty());
		}
		if (previous != null && !observedAt.isAfter(previous.observedAt())) {
			return new Outcome(previous, Optional.empty());
		}
		Band band = policy.bandOf(co2Intensity);
		BandState next = new BandState(band, observedAt);
		if (previous != null && previous.band() == band) {
			return new Outcome(next, Optional.empty());
		}
		Band before = previous == null ? null : previous.band();
		return new Outcome(next, Optional.of(new BandChange(scope, band, before, observedAt, co2Intensity)));
	}
}
