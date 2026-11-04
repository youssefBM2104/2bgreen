package com.twobgreen.platform.advisory.infrastructure;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings {@code grid.client.*} ({@code application.properties}). {@code grid.client.mode} is not here: it only
 * selects the bean in {@link GridClientConfig}.
 *
 * @param baseUrl where grid-intelligence listens
 * @param connectTimeout how long to wait for a connection
 * @param readTimeout how long to wait for an answer: above grid-intelligence's 3 s database timeout, so its own 503
 *        arrives before this gives up
 */
@ConfigurationProperties("grid.client")
record GridClientProperties(URI baseUrl, Duration connectTimeout, Duration readTimeout) {

	GridClientProperties {
		if (baseUrl == null) {
			throw new IllegalArgumentException("grid.client.base-url is required");
		}
		if (connectTimeout == null || readTimeout == null) {
			throw new IllegalArgumentException("grid.client.connect-timeout and read-timeout are required");
		}
	}
}
