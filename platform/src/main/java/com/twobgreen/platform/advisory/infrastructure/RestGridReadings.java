package com.twobgreen.platform.advisory.infrastructure;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.twobgreen.platform.advisory.application.GridReadings;
import com.twobgreen.platform.advisory.application.GridUnavailableException;
import com.twobgreen.platform.advisory.domain.GridReading;

/**
 * {@link GridReadings} on the read API of grid-intelligence (contract K2, {@code docs/api/openapi.yaml}).
 * <p>
 * "No data yet" is told by the error {@code code}, not by the status: Spring also answers 404 on an unknown route,
 * without a {@code code}, and that is a broken service, not an empty one.
 */
class RestGridReadings implements GridReadings {

	private static final String LATEST_NATIONAL = "/api/v1/national/snapshots/latest";

	private static final String NO_DATA = "NO_DATA";

	private final RestClient rest;

	RestGridReadings(RestClient rest) {
		this.rest = rest;
	}

	static RestGridReadings create(GridClientProperties properties) {
		HttpClient http = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(http);
		requestFactory.setReadTimeout(properties.readTimeout());
		RestClient rest = RestClient.builder()
				.baseUrl(properties.baseUrl().toString())
				.requestFactory(requestFactory)
				.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
				.build();
		return new RestGridReadings(rest);
	}

	@Override
	public Optional<GridReading> latestNational() {
		try {
			return rest.get()
					.uri(LATEST_NATIONAL)
					.exchange((request, response) -> {
						HttpStatusCode status = response.getStatusCode();
						String body = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
						if (status.is2xxSuccessful()) {
							return Optional.of(GridReadingJson.parse(body));
						}
						if (status.isSameCodeAs(HttpStatus.NOT_FOUND) && NO_DATA.equals(GridReadingJson.errorCode(body))) {
							return Optional.<GridReading>empty();
						}
						throw new GridUnavailableException("grid-intelligence answered HTTP " + status.value());
					});
		}
		catch (ResourceAccessException e) {
			throw new GridUnavailableException(
					"grid-intelligence gave no response: " + e.getMostSpecificCause().getClass().getSimpleName(), e);
		}
		catch (IllegalArgumentException e) {
			throw new GridUnavailableException("grid-intelligence gave an unreadable response: " + e.getMessage(), e);
		}
	}
}
