package com.twobgreen.gridintelligence.infrastructure.odre;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates the ODRÉ client from the {@code odre.*} settings. Building it opens no connection, so it is active on
 * every profile.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OdreProperties.class)
class OdreClientConfig {

	@Bean
	OdreClient odreClient(OdreProperties properties) {
		return OdreClient.create(properties);
	}
}
