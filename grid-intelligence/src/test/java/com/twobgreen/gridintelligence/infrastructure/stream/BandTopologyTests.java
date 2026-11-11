package com.twobgreen.gridintelligence.infrastructure.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Properties;

import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.twobgreen.gridintelligence.application.TrackBand;
import com.twobgreen.gridintelligence.domain.BandPolicy;
import com.twobgreen.gridintelligence.infrastructure.messaging.BandMessages;
import com.twobgreen.gridintelligence.infrastructure.messaging.SnapshotMessages;
import com.twobgreen.gridintelligence.infrastructure.messaging.Topics;

/** The band topology without Kafka: stored events in, band-changed events out, with a real (local) state store. */
class BandTopologyTests {

	private static final Instant T0 = Instant.parse("2026-10-20T12:00:00Z");

	private final JsonMapper json = JsonMapper.builder().build();

	private TopologyTestDriver driver;

	private TestInputTopic<String, String> stored;

	private TestOutputTopic<String, String> bands;

	@BeforeEach
	void startTopology() {
		StreamsBuilder builder = new StreamsBuilder();
		TrackBand track = new TrackBand(new BandPolicy(BigDecimal.valueOf(20), BigDecimal.valueOf(40)));
		BandTopology.build(builder, track, new SnapshotMessages(), new BandMessages());
		Properties properties = new Properties();
		properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "grid-stream-band-test");
		properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:9092");
		driver = new TopologyTestDriver(builder.build(), properties);
		stored = driver.createInputTopic(Topics.SNAPSHOT_STORED, new StringSerializer(), new StringSerializer());
		bands = driver.createOutputTopic(Topics.BAND_CHANGED, new StringDeserializer(), new StringDeserializer());
	}

	@AfterEach
	void stopTopology() {
		driver.close();
	}

	@Test
	void greenGreenAmberAmberGreenProducesExactlyThreeEvents() {
		int[] co2 = { 10, 15, 30, 35, 12 };
		for (int i = 0; i < co2.length; i++) {
			send("national", i, co2[i]);
		}

		List<JsonNode> events = events();

		assertThat(events).hasSize(3);
		assertThat(events).extracting(event -> event.path("band").stringValue())
				.containsExactly("GREEN", "AMBER", "GREEN");
		assertThat(events.get(0).path("previousBand").isNull()).isTrue();
		assertThat(events.get(1).path("previousBand").stringValue()).isEqualTo("GREEN");
		assertThat(events.get(2).path("previousBand").stringValue()).isEqualTo("AMBER");
		// each event names the snapshot that changed the band
		assertThat(events).extracting(event -> event.path("observedAt").stringValue())
				.containsExactly(at(0), at(2), at(4));
		assertThat(events.get(1).path("co2Intensity").intValue()).isEqualTo(30);
		assertThat(events.get(1).path("schemaVersion").intValue()).isEqualTo(1);
	}

	@Test
	void theEventIsKeyedByScope() {
		send("national", 0, 10);

		KeyValue<String, String> event = bands.readKeyValue();

		assertThat(event.key).isEqualTo("national");
		assertThat(json.readTree(event.value).path("scope").stringValue()).isEqualTo("national");
	}

	@Test
	void aReplayedMessageProducesNothing() {
		send("national", 0, 10);
		send("national", 1, 30);
		bands.readKeyValuesToList();

		send("national", 1, 30);
		send("national", 0, 10);
		send("national", 1, 90);

		assertThat(bands.isEmpty()).isTrue();
	}

	@Test
	void anOlderMessageProducesNothingAndDoesNotMoveTheStateBack() {
		send("national", 5, 30);
		bands.readKeyValuesToList();

		// late arrivals, in another band than the newest one
		send("national", 3, 10);
		send("national", 4, 90);
		assertThat(bands.isEmpty()).isTrue();

		// the state is still "amber at 5": a newer amber says nothing, a newer green changes it
		send("national", 6, 33);
		assertThat(bands.isEmpty()).isTrue();
		send("national", 7, 12);
		List<JsonNode> events = events();
		assertThat(events).hasSize(1);
		assertThat(events.get(0).path("band").stringValue()).isEqualTo("GREEN");
		assertThat(events.get(0).path("previousBand").stringValue()).isEqualTo("AMBER");
	}

	@Test
	void everyScopeHasItsOwnStateAndItsOwnFirstEvent() {
		send("national", 0, 10);
		send("region:76", 0, 50);
		send("national", 1, 12);
		send("region:76", 1, 55);
		send("region:32", 0, 10);

		List<KeyValue<String, String>> events = bands.readKeyValuesToList();

		assertThat(events).extracting(event -> event.key).containsExactly("national", "region:76", "region:32");
		assertThat(events).extracting(event -> json.readTree(event.value).path("band").stringValue())
				.containsExactly("GREEN", "RED", "GREEN");
	}

	@Test
	void aSnapshotWithoutCo2ProducesNothingAndDoesNotCountAsSeen() {
		send("region:76", 0, null);
		assertThat(bands.isEmpty()).isTrue();

		// it left no trace: this one, older than the CO2-less one, is the first of the scope
		send("region:76", 1, 10);
		send("region:76", 2, null);
		send("region:76", 1, 10);

		List<JsonNode> events = events();
		assertThat(events).hasSize(1);
		assertThat(events.get(0).path("previousBand").isNull()).isTrue();
	}

	@Test
	void theLimitsBelongToTheWorseBand() {
		send("national", 0, 19);
		send("national", 1, 20);
		send("national", 2, 39);
		send("national", 3, 40);

		assertThat(events()).extracting(event -> event.path("band").stringValue())
				.containsExactly("GREEN", "AMBER", "RED");
	}

	@Test
	void invalidMessagesAreSkippedAndTheStreamKeepsGoing() {
		stored.pipeInput("national", "not json");
		stored.pipeInput("national", "{\"schemaVersion\": 7}");
		send("national", 0, 10);

		assertThat(events()).hasSize(1);
	}

	private void send(String scope, int step, Integer co2) {
		stored.pipeInput(scope, message(scope, at(step), co2));
	}

	private List<JsonNode> events() {
		return bands.readValuesToList().stream().map(json::readTree).toList();
	}

	/** One reading every 15 minutes from 12:00Z. */
	private static String at(int step) {
		return T0.plusSeconds(900L * step).toString();
	}

	/** A grid.snapshot-stored event, with only what the band step reads (K1: the rest may be absent). */
	private static String message(String scope, String observedAt, Integer co2) {
		return """
				{"schemaVersion": 1, "scope": "%s", "observedAt": "%s", "co2Intensity": %s, "consumption": 49970,
				 "quality": "PROVISIONAL", "estimated": false, "storedAt": "%s"}
				""".formatted(scope, observedAt, co2 == null ? "null" : co2, observedAt);
	}
}
