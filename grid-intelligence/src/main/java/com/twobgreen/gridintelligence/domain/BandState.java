package com.twobgreen.gridintelligence.domain;

import java.time.Instant;

/**
 * What the stream remembers of one scope: the band of the newest snapshot it has seen, and that snapshot's
 * {@code observedAt}. The second one is what makes a replayed or late snapshot recognisable.
 */
public record BandState(Band band, Instant observedAt) {

	public BandState {
		if (band == null || observedAt == null) {
			throw new IllegalArgumentException("band and observedAt are required");
		}
	}
}
