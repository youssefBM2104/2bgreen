package com.twobgreen.gridintelligence.application;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;

import com.twobgreen.gridintelligence.domain.GridSnapshot;

/**
 * Use case of the read API: the snapshots of a scope over a period, from the merged view (batch row wins, else the
 * provisional ones). The period is checked here, so that no adapter ever receives a request too big to answer.
 * <p>
 * {@code from} and {@code to} apply to the start of each 30-min period: a period is returned whole when its start is
 * in {@code [from, to)}.
 */
public class GetSnapshotRange {

	/**
	 * Widest period one call may ask for. At 30-min grain that is at most 17,568 rows, one JSON answer; regional
	 * trends need months, not years. A wider request is refused, not cut, so that a client never mistakes a partial
	 * answer for a complete one.
	 */
	public static final Duration MAX_RANGE = Duration.ofDays(366);

	private final SnapshotReader reader;

	public GetSnapshotRange(SnapshotReader reader) {
		this.reader = reader;
	}

	/**
	 * @param from {@code from} as received, null if absent
	 * @param to {@code to} as received, null if absent
	 * @throws InvalidRangeException if the period is missing, malformed, out of order or wider than {@link #MAX_RANGE}
	 */
	public SnapshotRange national(String from, String to) {
		return range(GridSnapshot.NATIONAL_SCOPE, from, to);
	}

	/**
	 * @param code the region code of the URL
	 * @throws UnknownScopeException if {@code code} is not an INSEE region code
	 * @throws InvalidRangeException see {@link #national}
	 */
	public SnapshotRange region(String code, String from, String to) {
		// The scope is checked before the period: a wrong region is the more basic mistake
		return range(RegionCodes.scopeOf(code), from, to);
	}

	private SnapshotRange range(String scope, String from, String to) {
		Instant start = instant("from", from);
		Instant end = instant("to", to);
		if (!start.isBefore(end)) {
			throw new InvalidRangeException("'from' must be before 'to'.");
		}
		if (Duration.between(start, end).compareTo(MAX_RANGE) > 0) {
			throw new InvalidRangeException("The range is wider than " + MAX_RANGE.toDays() + " days.");
		}
		return new SnapshotRange(scope, start, end, reader.range(scope, start, end));
	}

	private static Instant instant(String name, String value) {
		if (value == null || value.isBlank()) {
			throw new InvalidRangeException("'" + name + "' is required.");
		}
		try {
			return Instant.parse(value.strip());
		}
		catch (DateTimeParseException e) {
			throw new InvalidRangeException("'" + name + "' must be a UTC date-time, ISO-8601 with Z.");
		}
	}
}
