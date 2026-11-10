package com.twobgreen.platform.automation.domain;

import java.time.Instant;

/**
 * Result of {@link CrossingPolicy#decide}: the outcome, and the state to store after it.
 *
 * @param lastCrossingAt {@code observedAt} of the last snapshot that fired, null if none ever did
 */
public record Crossing(CrossingOutcome outcome, RuleStatus status, Instant lastCrossingAt) {

	public boolean fired() {
		return outcome == CrossingOutcome.FIRED;
	}
}
