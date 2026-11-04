package com.twobgreen.platform.advisory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TrafficLightPolicyTests {

	private static final Instant NOW = Instant.parse("2026-11-04T12:00:00Z");

	private final TrafficLightPolicy policy = new TrafficLightPolicy(new BigDecimal("20"), new BigDecimal("40"),
			Duration.ofHours(2));

	private static GridReading reading(BigDecimal co2, Instant observedAt) {
		return new GridReading("national", observedAt, co2, GridQuality.PROVISIONAL, false, observedAt);
	}

	@ParameterizedTest
	@CsvSource({
			"0, GREEN",
			"19.99, GREEN",
			"20, AMBER",
			"20.00, AMBER",
			"39.99, AMBER",
			"40, RED",
			"65, RED"
	})
	void levelFollowsTheLimits(BigDecimal co2, TrafficLightLevel expected) {
		assertThat(policy.evaluate(reading(co2, NOW), NOW).level()).isEqualTo(expected);
	}

	@Test
	void zeroIsAMeasureButMissingIsUnknown() {
		assertThat(policy.evaluate(reading(BigDecimal.ZERO, NOW), NOW).level()).isEqualTo(TrafficLightLevel.GREEN);
		assertThat(policy.evaluate(reading(null, NOW), NOW).level()).isEqualTo(TrafficLightLevel.UNKNOWN);
	}

	@Test
	void aReadingOfExactlyTheMaxAgeIsStillCurrent() {
		TrafficLight light = policy.evaluate(reading(new BigDecimal("10"), NOW.minus(Duration.ofHours(2))), NOW);

		assertThat(light.stale()).isFalse();
		assertThat(light.level()).isEqualTo(TrafficLightLevel.GREEN);
	}

	@Test
	void anOlderReadingShowsNoColourAndKeepsItsValueAndDate() {
		Instant observedAt = NOW.minus(Duration.ofHours(2)).minusSeconds(1);

		TrafficLight light = policy.evaluate(reading(new BigDecimal("10"), observedAt), NOW);

		assertThat(light).isEqualTo(new TrafficLight(TrafficLightLevel.UNKNOWN, new BigDecimal("10"), observedAt, true));
	}

	@Test
	void refusesLimitsThatMakeNoSense() {
		Duration age = Duration.ofHours(2);
		BigDecimal twenty = new BigDecimal("20");

		assertThatThrownBy(() -> new TrafficLightPolicy(twenty, twenty, age)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new TrafficLightPolicy(null, twenty, age)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new TrafficLightPolicy(twenty, new BigDecimal("40"), Duration.ZERO))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
