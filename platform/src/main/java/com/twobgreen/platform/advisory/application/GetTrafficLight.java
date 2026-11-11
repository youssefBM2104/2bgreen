package com.twobgreen.platform.advisory.application;

import java.time.Clock;
import java.util.Optional;

import com.twobgreen.platform.advisory.GridUnavailableException;
import com.twobgreen.platform.advisory.domain.TrafficLight;
import com.twobgreen.platform.advisory.domain.TrafficLightPolicy;

/** Use case of the citizen space: the traffic light of the national grid right now. */
public class GetTrafficLight {

	private final GridReadings readings;

	private final TrafficLightPolicy policy;

	private final Clock clock;

	public GetTrafficLight(GridReadings readings, TrafficLightPolicy policy, Clock clock) {
		this.readings = readings;
		this.policy = policy;
		this.clock = clock;
	}

	/**
	 * @return the light, empty if no reading is stored yet
	 * @throws GridUnavailableException if the source cannot answer
	 */
	public Optional<TrafficLight> current() {
		return readings.latestNational().map(reading -> policy.evaluate(reading, clock.instant()));
	}
}
