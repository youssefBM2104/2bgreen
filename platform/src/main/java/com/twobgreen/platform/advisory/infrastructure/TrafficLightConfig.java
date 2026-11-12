package com.twobgreen.platform.advisory.infrastructure;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.twobgreen.platform.advisory.GridIntensity;
import com.twobgreen.platform.advisory.GridSnapshots;
import com.twobgreen.platform.advisory.application.GetTrafficLight;
import com.twobgreen.platform.advisory.application.GridReadings;
import com.twobgreen.platform.advisory.application.ReadGridIntensity;
import com.twobgreen.platform.advisory.application.ReadGridSnapshots;
import com.twobgreen.platform.advisory.domain.TrafficLightPolicy;

/**
 * Creates the use cases from the {@code advisory.traffic-light.*} settings: the citizen traffic light, and
 * {@link GridIntensity} for the other modules. Both share one policy, so they always agree on a colour. Also
 * {@link GridSnapshots}, the same readings with their mix.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TrafficLightProperties.class)
class TrafficLightConfig {

	@Bean
	TrafficLightPolicy trafficLightPolicy(TrafficLightProperties properties) {
		return new TrafficLightPolicy(properties.greenBelow(), properties.amberBelow(), properties.maxAge());
	}

	@Bean
	GetTrafficLight getTrafficLight(GridReadings readings, TrafficLightPolicy policy) {
		return new GetTrafficLight(readings, policy, Clock.systemUTC());
	}

	@Bean
	GridIntensity gridIntensity(GridReadings readings, TrafficLightPolicy policy) {
		return new ReadGridIntensity(readings, policy, Clock.systemUTC());
	}

	/** The whole readings, for the modules that show more than an intensity (regional dashboard, U11). */
	@Bean
	GridSnapshots gridSnapshots(GridReadings readings) {
		return new ReadGridSnapshots(readings);
	}
}
