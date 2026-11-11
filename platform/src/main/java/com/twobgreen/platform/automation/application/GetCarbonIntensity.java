package com.twobgreen.platform.automation.application;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;

import com.twobgreen.platform.advisory.CurrentIntensity;
import com.twobgreen.platform.advisory.GridIntensity;
import com.twobgreen.platform.advisory.GridScope;
import com.twobgreen.platform.advisory.GridUnavailableException;

/**
 * Use case of the public API of the DevOps (U7): the carbon intensity of the grid, for scripts and CI pipelines.
 * Parameters arrive as text and are checked here, so that every mistake of the caller is one documented
 * {@link InvalidIntensityRequestException} and not a framework error without a {@code code}.
 */
public class GetCarbonIntensity {

	/** Widest range one call may ask for. Longer history is not what a pipeline needs to schedule a job. */
	public static final Duration MAX_RANGE = Duration.ofDays(7);

	private final GridIntensity grid;

	public GetCarbonIntensity(GridIntensity grid) {
		this.grid = grid;
	}

	/**
	 * @param scope {@code national} or {@code region:<code>}
	 * @return the latest reading, empty if none is stored yet
	 * @throws InvalidIntensityRequestException if the scope is malformed
	 * @throws GridUnavailableException if grid data cannot be read
	 */
	public Optional<CurrentIntensity> latest(String scope) {
		return grid.latest(scope(scope));
	}

	/**
	 * @param from start, included, UTC ISO-8601
	 * @param to end, excluded, UTC ISO-8601, after {@code from} and at most {@link #MAX_RANGE} later
	 * @throws InvalidIntensityRequestException if a parameter is missing or wrong
	 * @throws GridUnavailableException if grid data cannot be read
	 */
	public IntensitySeries between(String scope, String from, String to) {
		GridScope gridScope = scope(scope);
		Instant start = instant("from", from);
		Instant end = instant("to", to);
		if (!start.isBefore(end)) {
			throw new InvalidIntensityRequestException("'from' must be before 'to'.");
		}
		if (Duration.between(start, end).compareTo(MAX_RANGE) > 0) {
			throw new InvalidIntensityRequestException(
					"The range is wider than " + MAX_RANGE.toDays() + " days. Split it into several calls.");
		}
		return new IntensitySeries(gridScope, start, end, grid.between(gridScope, start, end));
	}

	private static GridScope scope(String value) {
		try {
			return new GridScope(value);
		}
		catch (IllegalArgumentException e) {
			throw new InvalidIntensityRequestException("'scope' must be 'national' or 'region:<two-digit code>'.");
		}
	}

	private static Instant instant(String name, String value) {
		if (value == null || value.isBlank()) {
			throw new InvalidIntensityRequestException("'" + name + "' is required.");
		}
		try {
			return Instant.parse(value);
		}
		catch (DateTimeParseException e) {
			throw new InvalidIntensityRequestException(
					"'" + name + "' must be an ISO-8601 date-time in UTC, such as 2026-11-10T00:00:00Z.");
		}
	}
}
