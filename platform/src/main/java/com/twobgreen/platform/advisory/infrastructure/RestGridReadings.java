package com.twobgreen.platform.advisory.infrastructure;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import com.twobgreen.platform.advisory.GridScope;
import com.twobgreen.platform.advisory.GridUnavailableException;
import com.twobgreen.platform.advisory.application.GridReadings;
import com.twobgreen.platform.advisory.domain.GridReading;

/**
 * {@link GridReadings} on the read API of grid-intelligence (contract K2, {@code docs/api/openapi.yaml}).
 * <p>
 * "No data yet" is told by the error {@code code}, not by the status: Spring also answers 404 on an unknown route,
 * without a {@code code}, and that is a broken service, not an empty one. Any other error, a K2 {@code 400} or
 * {@code SCOPE_UNKNOWN} included, means this client sent something K2 refuses: it is reported as unavailable, with
 * the code in the message for the logs.
 */
class RestGridReadings implements GridReadings {

	private static final String NATIONAL = "/api/v1/national/snapshots";

	private static final String REGION = "/api/v1/regions/{code}/snapshots";

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
	public Optional<GridReading> latest(GridScope scope) {
		return call(uri -> path(uri, scope, "/latest").build(), (status, body) -> {
			if (status.is2xxSuccessful()) {
				return Optional.of(GridReadingJson.parse(body));
			}
			if (status.isSameCodeAs(HttpStatus.NOT_FOUND) && NO_DATA.equals(GridReadingJson.errorCode(body))) {
				return Optional.<GridReading>empty();
			}
			throw refused(status, body);
		});
	}

	@Override
	public List<GridReading> between(GridScope scope, Instant from, Instant to) {
		return call(uri -> path(uri, scope, "")
				.queryParam("from", from.toString())
				.queryParam("to", to.toString())
				.build(), (status, body) -> {
					if (status.is2xxSuccessful()) {
						return GridReadingJson.parseRange(body);
					}
					throw refused(status, body);
				});
	}

	private static UriBuilder path(UriBuilder uri, GridScope scope, String suffix) {
		if (scope.isNational()) {
			return uri.path(NATIONAL + suffix);
		}
		// The code is two digits (checked by GridScope), nothing to encode
		return uri.path(REGION.replace("{code}", scope.regionCode()) + suffix);
	}

	private <T> T call(Function<UriBuilder, URI> uri, Answer<T> answer) {
		try {
			return rest.get()
					.uri(uri)
					.exchange((request, response) -> answer.read(response.getStatusCode(),
							StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8)));
		}
		catch (ResourceAccessException e) {
			throw new GridUnavailableException(
					"grid-intelligence gave no response: " + e.getMostSpecificCause().getClass().getSimpleName(), e);
		}
		catch (IllegalArgumentException e) {
			throw new GridUnavailableException("grid-intelligence gave an unreadable response: " + e.getMessage(), e);
		}
	}

	private static GridUnavailableException refused(HttpStatusCode status, String body) {
		String code = GridReadingJson.errorCode(body);
		return new GridUnavailableException(
				"grid-intelligence answered HTTP " + status.value() + (code == null ? "" : " " + code));
	}

	@FunctionalInterface
	private interface Answer<T> {

		T read(HttpStatusCode status, String body);
	}
}
