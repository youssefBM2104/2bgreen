package com.twobgreen.platform.advisory.infrastructure;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.twobgreen.platform.advisory.application.GetTrafficLight;
import com.twobgreen.platform.advisory.application.GridReadings;
import com.twobgreen.platform.advisory.domain.TrafficLightPolicy;

/** Creates the use case from the {@code advisory.traffic-light.*} settings. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TrafficLightProperties.class)
class TrafficLightConfig {

	@Bean
	GetTrafficLight getTrafficLight(GridReadings readings, TrafficLightProperties properties) {
		TrafficLightPolicy policy = new TrafficLightPolicy(properties.greenBelow(), properties.amberBelow(),
				properties.maxAge());
		return new GetTrafficLight(readings, policy, Clock.systemUTC());
	}
}
