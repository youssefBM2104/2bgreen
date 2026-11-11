package com.twobgreen.gridintelligence.infrastructure.stream;

import java.time.Instant;

import com.twobgreen.gridintelligence.domain.Band;
import com.twobgreen.gridintelligence.domain.BandState;

/**
 * How a {@link BandState} is written in the state store (and so in its changelog topic): plain text,
 * {@code AMBER@2026-10-20T14:45:00Z}. Readable with a console consumer, and nothing to version beyond this line.
 */
final class BandStateCodec {

	private static final char SEPARATOR = '@';

	private BandStateCodec() {
	}

	static String encode(BandState state) {
		return state.band().name() + SEPARATOR + state.observedAt();
	}

	/** {@code null} for no state. */
	static BandState decode(String text) {
		if (text == null) {
			return null;
		}
		int at = text.indexOf(SEPARATOR);
		if (at < 0) {
			throw new IllegalStateException("unreadable band state in the store");
		}
		return new BandState(Band.valueOf(text.substring(0, at)), Instant.parse(text.substring(at + 1)));
	}
}
