package com.twobgreen.platform.advisory.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.twobgreen.platform.advisory.application.GridReadings;

/**
 * Selects the source of {@link GridReadings} with {@code grid.client.mode}: {@code rest} (default, so a missing
 * setting can never serve fake data) or {@code stub}. Any other value leaves no bean and the app refuses to start.
 * Building the REST client opens no connection, so the app starts without grid-intelligence.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GridClientProperties.class)
class GridClientConfig {

	@Bean
	@ConditionalOnProperty(name = "grid.client.mode", havingValue = "rest", matchIfMissing = true)
	GridReadings restGridReadings(GridClientProperties properties) {
		return RestGridReadings.create(properties);
	}

	@Bean
	@ConditionalOnProperty(name = "grid.client.mode", havingValue = "stub")
	GridReadings stubGridReadings() {
		return StubGridReadings.fromContractExample();
	}
}
