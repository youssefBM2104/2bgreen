package com.twobgreen.platform.advisory.infrastructure;

import java.math.BigDecimal;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.twobgreen.platform.advisory.application.GetTrafficLight;
import com.twobgreen.platform.advisory.domain.TrafficLight;

/** The traffic light of the citizen space. */
@RestController
class TrafficLightController {

	private final GetTrafficLight getTrafficLight;

	private final TrafficLightProperties properties;

	TrafficLightController(GetTrafficLight getTrafficLight, TrafficLightProperties properties) {
		this.getTrafficLight = getTrafficLight;
		this.properties = properties;
	}

	@GetMapping("/api/v1/advisory/traffic-light")
	@PreAuthorize("hasRole('citizen')")
	TrafficLight current() {
		return getTrafficLight.current().orElseThrow(NoGridReadingException::new);
	}

	/**
	 * The limits behind the colours, so that a screen can offer them (the alert form of U2 suggests them as
	 * thresholds) without copying the configuration.
	 */
	@GetMapping("/api/v1/advisory/traffic-light/limits")
	@PreAuthorize("hasRole('citizen')")
	Limits limits() {
		return new Limits(properties.greenBelow(), properties.amberBelow());
	}

	/** gCO2eq/kWh. Below {@code greenBelow} the light is green, below {@code amberBelow} amber, else red. */
	record Limits(BigDecimal greenBelow, BigDecimal amberBelow) {
	}
}
