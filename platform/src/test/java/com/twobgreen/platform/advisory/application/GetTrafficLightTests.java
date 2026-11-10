package com.twobgreen.platform.advisory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.twobgreen.platform.advisory.domain.GridQuality;
import com.twobgreen.platform.advisory.domain.GridReading;
import com.twobgreen.platform.advisory.domain.TrafficLight;
import com.twobgreen.platform.advisory.domain.TrafficLightLevel;
import com.twobgreen.platform.advisory.domain.TrafficLightPolicy;

class GetTrafficLightTests {

	private static final Instant NOW = Instant.parse("2026-11-04T12:00:00Z");

	private static GetTrafficLight useCase(GridReadings readings) {
		return new GetTrafficLight(readings,
				new TrafficLightPolicy(new BigDecimal("20"), new BigDecimal("40"), Duration.ofHours(2)),
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void evaluatesTheLatestReadingAtTheCurrentTime() {
		Instant observedAt = NOW.minus(Duration.ofHours(3));
		GridReading reading = new GridReading("national", observedAt, new BigDecimal("10"), GridQuality.PROVISIONAL,
				false, observedAt);

		assertThat(useCase(() -> Optional.of(reading)).current()).contains(
				new TrafficLight(TrafficLightLevel.UNKNOWN, new BigDecimal("10"), observedAt, true));
	}

	@Test
	void noReadingIsNoLight() {
		assertThat(useCase(Optional::empty).current()).isEmpty();
	}

	@Test
	void anUnavailableSourceGoesToTheCaller() {
		GridReadings down = () -> {
			throw new GridUnavailableException("down");
		};

		assertThatThrownBy(() -> useCase(down).current()).isInstanceOf(GridUnavailableException.class);
	}
}
