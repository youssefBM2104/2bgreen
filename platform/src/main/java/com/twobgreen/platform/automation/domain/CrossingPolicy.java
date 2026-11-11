package com.twobgreen.platform.automation.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * Decides when a condition that is true or false at each snapshot deserves a notification. The same policy serves
 * alert rules, webhooks and, later, the pace of a target (U16).
 * <ul>
 * <li><b>Crossing.</b> A subject fires when its condition becomes true, then stays silent while it remains true. It is
 * armed again when the condition becomes false.</li>
 * <li><b>Replay.</b> A snapshot that is not newer than the last one evaluated is ignored: Kafka delivers at least
 * once, and one scope arrives in order.</li>
 * <li><b>Debounce.</b> After firing, a subject does not fire again before {@code minInterval}. A crossing in that
 * period is not lost: the subject stays armed and fires at the first later snapshot that still meets the
 * condition.</li>
 * <li><b>Old snapshots.</b> A snapshot older than {@code maxSnapshotAge} does not fire, the subject stays armed. After
 * an outage the backlog is read without telling users about a grid state that is over.</li>
 * </ul>
 * Time is the {@code observedAt} of the snapshots, so evaluating the same sequence twice gives the same result. Only
 * the age of a snapshot looks at the clock.
 *
 * @param minInterval shortest time between two notifications of one subject; zero turns the debounce off
 * @param maxSnapshotAge how old a snapshot can be and still notify
 */
public record CrossingPolicy(Duration minInterval, Duration maxSnapshotAge) {

	public CrossingPolicy {
		if (minInterval == null || minInterval.isNegative()) {
			throw new IllegalArgumentException("minInterval is required and cannot be negative");
		}
		if (maxSnapshotAge == null || maxSnapshotAge.isNegative() || maxSnapshotAge.isZero()) {
			throw new IllegalArgumentException("maxSnapshotAge is required and must be positive");
		}
	}

	/**
	 * @param previous state stored at the last evaluation, null if the subject was never evaluated
	 * @param conditionMet is the condition of the subject true for this snapshot
	 * @param observedAt {@code observedAt} of the snapshot
	 * @param now current time, only used to tell an old snapshot
	 */
	public Crossing decide(RuleState previous, boolean conditionMet, Instant observedAt, Instant now) {
		RuleStatus status = previous == null ? RuleStatus.ARMED : RuleStatus.valueOf(previous.state());
		Instant lastCrossingAt = previous == null ? null : previous.lastCrossingAt();
		Instant lastObservedAt = previous == null ? null : previous.lastObservedAt();
		if (lastObservedAt != null && !observedAt.isAfter(lastObservedAt)) {
			return new Crossing(CrossingOutcome.ALREADY_SEEN, status, lastCrossingAt);
		}
		if (!conditionMet) {
			CrossingOutcome outcome = status == RuleStatus.FIRED ? CrossingOutcome.REARMED : CrossingOutcome.UNCHANGED;
			return new Crossing(outcome, RuleStatus.ARMED, lastCrossingAt);
		}
		if (status == RuleStatus.FIRED) {
			return new Crossing(CrossingOutcome.UNCHANGED, RuleStatus.FIRED, lastCrossingAt);
		}
		boolean tooOld = observedAt.plus(maxSnapshotAge).isBefore(now);
		boolean tooSoon = lastCrossingAt != null && observedAt.isBefore(lastCrossingAt.plus(minInterval));
		if (tooOld || tooSoon) {
			return new Crossing(CrossingOutcome.HELD_BACK, RuleStatus.ARMED, lastCrossingAt);
		}
		return new Crossing(CrossingOutcome.FIRED, RuleStatus.FIRED, observedAt);
	}
}
