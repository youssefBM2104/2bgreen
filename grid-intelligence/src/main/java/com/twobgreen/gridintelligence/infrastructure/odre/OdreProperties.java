package com.twobgreen.gridintelligence.infrastructure.odre;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings {@code odre.*}. The base URL is configurable so that tests point the client at a local fake server.
 *
 * @param baseUrl root of the Explore v2.1 API, e.g. {@code https://odre.opendatasoft.com/api/explore/v2.1}
 * @param connectTimeout time allowed to open the connection
 * @param readTimeout time allowed for ODRÉ to answer once the request is sent
 * @param pageSize rows per request, 1 to 100 (ODRÉ refuses a {@code limit} above 100)
 */
@ConfigurationProperties("odre")
record OdreProperties(URI baseUrl, Duration connectTimeout, Duration readTimeout, int pageSize) {

	static final int MAX_PAGE_SIZE = 100;

	OdreProperties {
		if (baseUrl == null) {
			throw new IllegalArgumentException("odre.base-url is required");
		}
		if (connectTimeout == null || readTimeout == null) {
			throw new IllegalArgumentException("odre.connect-timeout and odre.read-timeout are required");
		}
		if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
			throw new IllegalArgumentException("odre.page-size must be between 1 and " + MAX_PAGE_SIZE);
		}
	}
}
