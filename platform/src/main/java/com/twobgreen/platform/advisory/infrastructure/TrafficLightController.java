package com.twobgreen.platform.advisory.infrastructure;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.twobgreen.platform.advisory.application.GetTrafficLight;
import com.twobgreen.platform.advisory.domain.TrafficLight;

/** The traffic light of the citizen space. */
@RestController
class TrafficLightController {

	private final GetTrafficLight getTrafficLight;

	TrafficLightController(GetTrafficLight getTrafficLight) {
		this.getTrafficLight = getTrafficLight;
	}

	@GetMapping("/api/v1/advisory/traffic-light")
	@PreAuthorize("hasRole('citizen')")
	TrafficLight current() {
		return getTrafficLight.current().orElseThrow(NoGridReadingException::new);
	}
}
