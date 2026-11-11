package com.twobgreen.platform.advisory.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.twobgreen.platform.advisory.CurrentIntensity;
import com.twobgreen.platform.advisory.GridIntensity;
import com.twobgreen.platform.advisory.GridScope;
import com.twobgreen.platform.advisory.IntensityReading;
import com.twobgreen.platform.advisory.domain.GridReading;
import com.twobgreen.platform.advisory.domain.TrafficLight;
import com.twobgreen.platform.advisory.domain.TrafficLightPolicy;

/**
 * {@link GridIntensity} for the other modules: the same readings and the same traffic light as the citizen space, so
 * the DevOps API and the citizen page can never show two colours for one reading.
 */
public class ReadGridIntensity implements GridIntensity {

	private final GridReadings readings;

	private final TrafficLightPolicy policy;

	private final Clock clock;

	public ReadGridIntensity(GridReadings readings, TrafficLightPolicy policy, Clock clock) {
		this.readings = readings;
		this.policy = policy;
		this.clock = clock;
	}

	@Override
	public Optional<CurrentIntensity> latest(GridScope scope) {
		return readings.latest(scope).map(reading -> {
			TrafficLight light = policy.evaluate(reading, clock.instant());
			return new CurrentIntensity(published(scope, reading), light.level().name(), light.stale());
		});
	}

	@Override
	public List<IntensityReading> between(GridScope scope, Instant from, Instant to) {
		return readings.between(scope, from, to).stream().map(reading -> published(scope, reading)).toList();
	}

	private static IntensityReading published(GridScope scope, GridReading reading) {
		return new IntensityReading(scope, reading.observedAt(), reading.co2Intensity(), reading.quality().name(),
				reading.estimated(), reading.storedAt());
	}
}
