package com.twobgreen.gridintelligence.infrastructure.odre;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.twobgreen.gridintelligence.application.ConsolidatedOdreGateway;
import com.twobgreen.gridintelligence.application.OdreGateway;
import com.twobgreen.gridintelligence.application.RegionalOdreGateway;

/**
 * Creates the ODRÉ client from the {@code odre.*} settings, and the gateways ({@link OdreGateway}, {@link RegionalOdreGateway}) the rest of the app uses.
 * Building them opens no connection, so they are active on every profile.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OdreProperties.class)
class OdreClientConfig {

	@Bean
	OdreClient odreClient(OdreProperties properties) {
		return OdreClient.create(properties);
	}

	@Bean
	OdreGateway odreGateway(OdreClient client) {
		return new NationalRealtimeGateway(client, Clock.systemUTC());
	}

	@Bean
	RegionalOdreGateway regionalOdreGateway(OdreClient client) {
		return new RegionalRealtimeGateway(client, Clock.systemUTC());
	}

	@Bean
	ConsolidatedOdreGateway consolidatedOdreGateway(OdreClient client) {
		return new NationalConsolidatedGateway(client);
	}
}
