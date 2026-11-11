package com.twobgreen.platform.advisory.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.twobgreen.platform.advisory.GridScope;
import com.twobgreen.platform.advisory.GridUnavailableException;
import com.twobgreen.platform.advisory.domain.GridQuality;
import com.twobgreen.platform.advisory.domain.GridReading;

/** The adapter against a local fake server and the real K2 examples: no Docker, no grid-intelligence. */
class RestGridReadingsTests {

	private final FakeGridServer server = new FakeGridServer();

	private final RestGridReadings readings = readingsOn(server.baseUrl(), Duration.ofSeconds(2));

	@AfterEach
	void stopServer() {
		server.close();
	}

	private static RestGridReadings readingsOn(URI baseUrl, Duration readTimeout) {
		return RestGridReadings.create(new GridClientProperties(baseUrl, Duration.ofSeconds(1), readTimeout));
	}

	@Test
	void readsTheContractExample() {
		server.reply(200, K2Examples.read("latest-national.json"));

		assertThat(readings.latestNational()).contains(new GridReading(
				"national",
				Instant.parse("2026-10-20T14:45:00Z"),
				new BigDecimal("29"),
				GridQuality.PROVISIONAL,
				false,
				Instant.parse("2026-10-20T15:03:12Z")));
	}

	@Test
	void callsTheFrozenPath() {
		server.reply(200, K2Examples.read("latest-national.json"));

		readings.latestNational();

		assertThat(server.requests()).extracting(URI::getPath).containsExactly("/api/v1/national/snapshots/latest");
	}

	@Test
	void acceptsTheContractExampleWithMissingValues() {
		server.reply(200, K2Examples.read("latest-national-missing-values.json"));

		assertThat(readings.latestNational()).isPresent();
	}

	@ParameterizedTest
	@ValueSource(strings = { "\"co2Intensity\": null,", "" })
	void missingCo2IsNullNeverZero(String co2) {
		server.reply(200, """
				{"schemaVersion": 1, "scope": "national", "observedAt": "2026-10-20T14:45:00Z", %s
				 "quality": "PROVISIONAL", "estimated": false, "storedAt": "2026-10-20T15:03:12Z"}
				""".formatted(co2));

		assertThat(readings.latestNational()).get().extracting(GridReading::co2Intensity).isNull();
	}

	@Test
	void ignoresUnknownFields() {
		server.reply(200, K2Examples.read("latest-national.json").replaceFirst("\\{", "{\"addedLater\": {\"a\": 1},"));

		assertThat(readings.latestNational()).isPresent();
	}

	@Test
	void noDataIsAnEmptyAnswerNotAnError() {
		server.reply(404, K2Examples.read("error-no-data.json"));

		assertThat(readings.latestNational()).isEqualTo(Optional.empty());
	}

	@Test
	void notFoundWithoutTheNoDataCodeIsABrokenService() {
		// What Spring answers on an unknown route: a 404 that does not mean "no data yet"
		server.reply(404, "{\"title\": \"Not Found\", \"status\": 404}");

		assertUnavailable(readings, "HTTP 404");
	}

	@Test
	void notFoundWithAnotherBodyIsABrokenService() {
		server.reply(404, "<html>Not found</html>");

		assertUnavailable(readings, "HTTP 404");
	}

	@ParameterizedTest
	@ValueSource(strings = { "error-unavailable.json:503", "error-internal.json:500" })
	void serverErrorsMeanUnavailable(String example) {
		String[] parts = example.split(":");
		server.reply(Integer.parseInt(parts[1]), K2Examples.read(parts[0]));

		assertUnavailable(readings, "HTTP " + parts[1]);
	}

	@Test
	void connectionRefusedMeansUnavailable() throws IOException {
		URI closedPort;
		try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
			closedPort = URI.create("http://" + socket.getInetAddress().getHostAddress() + ":" + socket.getLocalPort());
		}

		assertUnavailable(readingsOn(closedPort, Duration.ofSeconds(1)), "no response");
	}

	@Test
	void slowAnswerMeansUnavailable() {
		server.replyAfter(Duration.ofSeconds(2), 200, K2Examples.read("latest-national.json"));

		assertUnavailable(readingsOn(server.baseUrl(), Duration.ofMillis(200)), "no response");
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"not json",
			"",
			"[]",
			"{}",
			// Each one is the K2 example with one thing broken
			"{\"schemaVersion\": 2, \"scope\": \"national\", \"observedAt\": \"2026-10-20T14:45:00Z\", \"quality\": \"PROVISIONAL\", \"estimated\": false, \"storedAt\": \"2026-10-20T15:03:12Z\"}",
			"{\"schemaVersion\": 1, \"observedAt\": \"2026-10-20T14:45:00Z\", \"quality\": \"PROVISIONAL\", \"estimated\": false, \"storedAt\": \"2026-10-20T15:03:12Z\"}",
			"{\"schemaVersion\": 1, \"scope\": \"national\", \"observedAt\": \"yesterday\", \"quality\": \"PROVISIONAL\", \"estimated\": false, \"storedAt\": \"2026-10-20T15:03:12Z\"}",
			"{\"schemaVersion\": 1, \"scope\": \"national\", \"observedAt\": \"2026-10-20T14:45:00Z\", \"co2Intensity\": \"29\", \"quality\": \"PROVISIONAL\", \"estimated\": false, \"storedAt\": \"2026-10-20T15:03:12Z\"}",
			"{\"schemaVersion\": 1, \"scope\": \"national\", \"observedAt\": \"2026-10-20T14:45:00Z\", \"quality\": \"GUESSED\", \"estimated\": false, \"storedAt\": \"2026-10-20T15:03:12Z\"}",
			"{\"schemaVersion\": 1, \"scope\": \"national\", \"observedAt\": \"2026-10-20T14:45:00Z\", \"quality\": \"PROVISIONAL\", \"storedAt\": \"2026-10-20T15:03:12Z\"}",
			"{\"schemaVersion\": 1, \"scope\": \"national\", \"observedAt\": \"2026-10-20T14:45:00Z\", \"quality\": \"PROVISIONAL\", \"estimated\": false}"
	})
	void unreadableAnswerMeansUnavailable(String body) {
		server.reply(200, body);

		assertUnavailable(readings, "unreadable");
	}

	@Test
	void latestOfARegionCallsItsFrozenPath() {
		server.reply(200, K2Examples.read("regions-latest-draft.json"));

		assertThat(readings.latest(new GridScope("region:76"))).get()
				.extracting(GridReading::scope, GridReading::co2Intensity)
				.containsExactly("region:76", null);
		assertThat(server.requests()).extracting(URI::getPath)
				.containsExactly("/api/v1/regions/76/snapshots/latest");
	}

	@Test
	void rangeReadsTheContractExampleInOrder() {
		server.reply(200, K2Examples.read("range-national.json"));
		Instant from = Instant.parse("2025-12-31T00:00:00Z");
		Instant to = Instant.parse("2026-10-21T00:00:00Z");

		List<GridReading> range = readings.between(GridScope.NATIONAL, from, to);

		assertThat(range).extracting(GridReading::quality)
				.containsExactly(GridQuality.DEFINITIVE, GridQuality.CONSOLIDATED, GridQuality.PROVISIONAL);
		assertThat(range.getFirst().co2Intensity()).isEqualByComparingTo("41");
		assertThat(server.requests()).singleElement().satisfies(uri -> {
			assertThat(uri.getPath()).isEqualTo("/api/v1/national/snapshots");
			assertThat(uri.getQuery()).isEqualTo("from=2025-12-31T00:00:00Z&to=2026-10-21T00:00:00Z");
		});
	}

	@Test
	void rangeOfARegionCallsItsFrozenPath() {
		server.reply(200, K2Examples.read("range-region.json"));

		assertThat(readings.between(new GridScope("region:76"), Instant.parse("2026-10-20T14:00:00Z"),
				Instant.parse("2026-10-20T15:00:00Z"))).isNotEmpty().allSatisfy(reading -> {
					assertThat(reading.scope()).isEqualTo("region:76");
					assertThat(reading.co2Intensity()).isNull();
				});
		assertThat(server.requests()).extracting(URI::getPath).containsExactly("/api/v1/regions/76/snapshots");
	}

	@Test
	void anEmptyRangeIsNoReading() {
		server.reply(200, "{\"scope\": \"national\", \"from\": \"2026-10-20T00:00:00Z\", "
				+ "\"to\": \"2026-10-21T00:00:00Z\", \"items\": []}");

		assertThat(readings.between(GridScope.NATIONAL, Instant.parse("2026-10-20T00:00:00Z"),
				Instant.parse("2026-10-21T00:00:00Z"))).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "error-range-too-large.json:400", "error-no-data.json:404", "error-unavailable.json:503" })
	void aRangeRefusedByK2IsUnavailableWithItsCode(String example) {
		String[] parts = example.split(":");
		server.reply(Integer.parseInt(parts[1]), K2Examples.read(parts[0]));

		assertThatThrownBy(() -> readings.between(GridScope.NATIONAL, Instant.parse("2026-10-20T00:00:00Z"),
				Instant.parse("2026-10-21T00:00:00Z")))
				.isInstanceOf(GridUnavailableException.class)
				.hasMessageContaining("HTTP " + parts[1] + " ");
	}

	@ParameterizedTest
	@ValueSource(strings = { "{}", "{\"items\": {}}", "{\"items\": [1]}", "{\"items\": [{\"schemaVersion\": 1}]}" })
	void anUnreadableRangeIsUnavailable(String body) {
		server.reply(200, body);

		assertThatThrownBy(() -> readings.between(GridScope.NATIONAL, Instant.parse("2026-10-20T00:00:00Z"),
				Instant.parse("2026-10-21T00:00:00Z")))
				.isInstanceOf(GridUnavailableException.class)
				.hasMessageContaining("unreadable");
	}

	private static void assertUnavailable(RestGridReadings readings, String message) {
		assertThatThrownBy(readings::latestNational)
				.isInstanceOf(GridUnavailableException.class)
				.hasMessageContaining(message);
	}
}
