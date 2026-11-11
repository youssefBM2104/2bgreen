package com.twobgreen.platform.advisory.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.twobgreen.platform.advisory.CurrentIntensity;
import com.twobgreen.platform.advisory.GridScope;
import com.twobgreen.platform.advisory.IntensityReading;
import com.twobgreen.platform.advisory.domain.GridQuality;
import com.twobgreen.platform.advisory.domain.GridReading;
import com.twobgreen.platform.advisory.domain.TrafficLightPolicy;

class ReadGridIntensityTests {

	private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");

	private static final GridScope REGION = new GridScope("region:76");

	private static GridReading reading(String co2, Instant observedAt, GridQuality quality) {
		return new GridReading("national", observedAt, co2 == null ? null : new BigDecimal(co2), quality, false,
				observedAt.plusSeconds(600));
	}

	private static ReadGridIntensity readGridIntensity(Optional<GridReading> latest, List<GridReading> range) {
		GridReadings readings = new GridReadings() {

			@Override
			public Optional<GridReading> latest(GridScope scope) {
				return latest;
			}

			@Override
			public List<GridReading> between(GridScope scope, Instant from, Instant to) {
				return range;
			}
		};
		return new ReadGridIntensity(readings,
				new TrafficLightPolicy(new BigDecimal("20"), new BigDecimal("40"), Duration.ofHours(2)),
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void latestHasTheColourOfTheCitizenLight() {
		Instant observedAt = NOW.minus(Duration.ofMinutes(45));
		GridReading reading = reading("40", observedAt, GridQuality.PROVISIONAL);

		assertThat(readGridIntensity(Optional.of(reading), List.of()).latest(GridScope.NATIONAL)).contains(
				new CurrentIntensity(new IntensityReading(GridScope.NATIONAL, observedAt, new BigDecimal("40"),
						"PROVISIONAL", false, observedAt.plusSeconds(600)), "RED", false));
	}

	@Test
	void anOldReadingIsStaleAndHasNoColour() {
		GridReading reading = reading("10", NOW.minus(Duration.ofHours(3)), GridQuality.PROVISIONAL);

		CurrentIntensity current = readGridIntensity(Optional.of(reading), List.of()).latest(GridScope.NATIONAL)
				.orElseThrow();

		assertThat(current.trafficLight()).isEqualTo("UNKNOWN");
		assertThat(current.stale()).isTrue();
	}

	@Test
	void rangeKeepsOrderValuesAndQuality() {
		Instant first = Instant.parse("2026-11-09T00:00:00Z");
		List<GridReading> range = List.of(reading("31.5", first, GridQuality.CONSOLIDATED),
				reading(null, first.plusSeconds(1800), GridQuality.PROVISIONAL));

		List<IntensityReading> items = readGridIntensity(Optional.empty(), range).between(REGION, first,
				first.plusSeconds(3600));

		assertThat(items).extracting(IntensityReading::scope, IntensityReading::co2Intensity, IntensityReading::quality)
				.containsExactly(
						org.assertj.core.groups.Tuple.tuple(REGION, new BigDecimal("31.5"), "CONSOLIDATED"),
						org.assertj.core.groups.Tuple.tuple(REGION, null, "PROVISIONAL"));
	}
}
