package com.twobgreen.platform.automation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class CrossingPolicyTests {

	private static final Instant T0 = Instant.parse("2026-11-10T12:00:00Z");

	private static final Duration STEP = Duration.ofMinutes(15);

	private static final CrossingPolicy NO_DEBOUNCE = new CrossingPolicy(Duration.ZERO, Duration.ofHours(2));

	private static final CrossingPolicy ONE_HOUR = new CrossingPolicy(Duration.ofHours(1), Duration.ofHours(2));

	@Test
	void firesOncePerCrossing() {
		// U2 with a limit of 30: 45, 38, 28, 25, 33, 29
		assertThat(outcomes(NO_DEBOUNCE, false, false, true, true, false, true)).containsExactly(
				CrossingOutcome.UNCHANGED,
				CrossingOutcome.UNCHANGED,
				CrossingOutcome.FIRED,
				CrossingOutcome.UNCHANGED,
				CrossingOutcome.REARMED,
				CrossingOutcome.FIRED);
	}

	@Test
	void conditionAlreadyMetAtTheFirstEvaluationFires() {
		assertThat(NO_DEBOUNCE.decide(null, true, T0, T0).outcome()).isEqualTo(CrossingOutcome.FIRED);
	}

	@Test
	void snapshotAlreadySeenOrOlderIsAReplay() {
		RuleState fired = state(RuleStatus.FIRED, T0, T0);

		assertThat(NO_DEBOUNCE.decide(fired, true, T0, T0).outcome()).isEqualTo(CrossingOutcome.ALREADY_SEEN);
		assertThat(NO_DEBOUNCE.decide(fired, false, T0.minus(STEP), T0).outcome())
				.isEqualTo(CrossingOutcome.ALREADY_SEEN);
	}

	@Test
	void debounceHoldsACrossingBackThenLetsItFire() {
		// Fired at T0, back above, then below again 30 and 45 minutes later: too soon
		RuleState rearmed = state(RuleStatus.ARMED, T0, T0.plus(STEP));

		Crossing tooSoon = ONE_HOUR.decide(rearmed, true, T0.plus(STEP.multipliedBy(3)), T0.plus(Duration.ofHours(1)));
		assertThat(tooSoon.outcome()).isEqualTo(CrossingOutcome.HELD_BACK);
		assertThat(tooSoon.status()).isEqualTo(RuleStatus.ARMED);
		assertThat(tooSoon.lastCrossingAt()).isEqualTo(T0);

		// Exactly one hour after the last notification, still below: it fires
		Instant oneHourLater = T0.plus(Duration.ofHours(1));
		Crossing later = ONE_HOUR.decide(state(RuleStatus.ARMED, T0, T0.plus(STEP.multipliedBy(3))), true,
				oneHourLater, oneHourLater);
		assertThat(later.outcome()).isEqualTo(CrossingOutcome.FIRED);
		assertThat(later.lastCrossingAt()).isEqualTo(oneHourLater);
	}

	@Test
	void oldSnapshotDoesNotFireAndLeavesTheSubjectArmed() {
		Instant now = T0.plus(Duration.ofHours(2)).plusSeconds(1);

		Crossing old = NO_DEBOUNCE.decide(null, true, T0, now);

		assertThat(old.outcome()).isEqualTo(CrossingOutcome.HELD_BACK);
		assertThat(old.status()).isEqualTo(RuleStatus.ARMED);
		// A snapshot of exactly the maximum age still counts
		assertThat(NO_DEBOUNCE.decide(null, true, T0, T0.plus(Duration.ofHours(2))).outcome())
				.isEqualTo(CrossingOutcome.FIRED);
	}

	@Test
	void settingsAreChecked() {
		assertThatThrownBy(() -> new CrossingPolicy(Duration.ofSeconds(-1), Duration.ofHours(2)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new CrossingPolicy(Duration.ZERO, Duration.ZERO))
				.isInstanceOf(IllegalArgumentException.class);
	}

	/** Feeds one reading every 15 minutes, each state coming from the decision before, as the engine does. */
	private static List<CrossingOutcome> outcomes(CrossingPolicy policy, boolean... conditionMet) {
		List<CrossingOutcome> outcomes = new ArrayList<>();
		RuleState state = null;
		Instant observedAt = T0;
		for (boolean met : conditionMet) {
			Crossing crossing = policy.decide(state, met, observedAt, observedAt);
			outcomes.add(crossing.outcome());
			state = state(crossing.status(), crossing.lastCrossingAt(), observedAt);
			observedAt = observedAt.plus(STEP);
		}
		return outcomes;
	}

	private static RuleState state(RuleStatus status, Instant lastCrossingAt, Instant lastObservedAt) {
		return new RuleState(1L, "user", RuleSubject.ALERT_RULE, 1L, status.name(), lastCrossingAt, lastObservedAt);
	}
}
