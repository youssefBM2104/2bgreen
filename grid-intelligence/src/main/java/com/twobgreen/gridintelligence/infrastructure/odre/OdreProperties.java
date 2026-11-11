package com.twobgreen.gridintelligence.infrastructure.odre;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * Settings {@code odre.*}. The base URL is configurable so that tests point the client at a local fake server.
 *
 * @param baseUrl root of the Explore v2.1 API, e.g. {@code https://odre.opendatasoft.com/api/explore/v2.1}
 * @param connectTimeout time allowed to open the connection
 * @param readTimeout time allowed for ODRÉ to answer once the request is sent
 * @param pageSize rows per request, 1 to 100 (ODRÉ refuses a {@code limit} above 100)
 * @param exportReadTimeout same as {@code readTimeout}, for the whole-year exports of the batch: ODRÉ builds the file
 *        before sending its first byte, which takes minutes for a regional year. Defaults to {@code readTimeout}.
 */
@ConfigurationProperties("odre")
record OdreProperties(URI baseUrl, Duration connectTimeout, Duration readTimeout, int pageSize,
		Duration exportReadTimeout) {

	static final int MAX_PAGE_SIZE = 100;

	@ConstructorBinding
	OdreProperties(URI baseUrl, Duration connectTimeout, Duration readTimeout, int pageSize,
			Duration exportReadTimeout) {
		if (baseUrl == null) {
			throw new IllegalArgumentException("odre.base-url is required");
		}
		if (connectTimeout == null || readTimeout == null) {
			throw new IllegalArgumentException("odre.connect-timeout and odre.read-timeout are required");
		}
		if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
			throw new IllegalArgumentException("odre.page-size must be between 1 and " + MAX_PAGE_SIZE);
		}
		this.baseUrl = baseUrl;
		this.connectTimeout = connectTimeout;
		this.readTimeout = readTimeout;
		this.pageSize = pageSize;
		this.exportReadTimeout = exportReadTimeout == null ? readTimeout : exportReadTimeout;
	}

	/** Exports wait as long as the other calls. */
	OdreProperties(URI baseUrl, Duration connectTimeout, Duration readTimeout, int pageSize) {
		this(baseUrl, connectTimeout, readTimeout, pageSize, null);
	}
}
