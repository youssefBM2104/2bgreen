package com.twobgreen.gridintelligence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.FileSystemUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.twobgreen.gridintelligence.infrastructure.messaging.Topics;

/**
 * G9 on real Kafka and TimescaleDB containers: the band step with its state store and its changelog topic. The
 * decisions themselves are covered without Kafka (BandTopologyTests); this is about what only a broker can show.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
		"spring.kafka.streams.properties.commit.interval.ms=100",
		"spring.kafka.streams.properties.consumer.session.timeout.ms=6000",
		"spring.kafka.consumer.auto-offset-reset=earliest",
		// A directory of our own, so the test can delete the local state and force a restore from the changelog
		"spring.kafka.streams.state-dir=" + BandStreamIntegrationTests.STATE_DIR
})
@ActiveProfiles("stream")
class BandStreamIntegrationTests {

	static final String STATE_DIR = "target/it-band-state";

	private static final Duration LIMIT = Duration.ofSeconds(60);

	private static final Instant T0 = Instant.parse("2026-10-20T12:00:00Z");

	private final JsonMapper json = JsonMapper.builder().build();

	// Topics are shared by every test of the run: this scope is this test's alone
	private final String scope = "test:band-" + UUID.randomUUID();

	@Autowired
	KafkaTemplate<String, String> kafka;

	@Autowired
	ConsumerFactory<String, String> consumerFactory;

	@Autowired
	StreamsBuilderFactoryBean streams;

	private Consumer<String, String> bandEvents;

	@BeforeEach
	void listenToBandEvents() {
		bandEvents = consumerFactory.createConsumer("it-band-" + UUID.randomUUID(), "");
		bandEvents.subscribe(List.of(Topics.BAND_CHANGED));
	}

	@AfterEach
	void stopListening() {
		bandEvents.close();
		if (!streams.isRunning()) {
			streams.start();
		}
	}

	@Test
	void aSnapshotThatChangesTheBandIsAnnouncedOnTheBandTopicKeyedByScope() {
		send(0, 15);

		List<ConsumerRecord<String, String>> records = awaitEvents(1);

		assertThat(records.get(0).key()).isEqualTo(scope);
		JsonNode event = json.readTree(records.get(0).value());
		assertThat(event.path("band").stringValue()).isEqualTo("GREEN");
		assertThat(event.path("previousBand").isNull()).isTrue();
		assertThat(event.path("observedAt").stringValue()).isEqualTo(T0.toString());
	}

	@Test
	void afterARestartWithoutLocalStateTheBandIsRestoredFromTheChangelogAndNothingIsAnnouncedAgain() throws Exception {
		send(0, 15);
		awaitEvents(1);

		streams.stop();
		FileSystemUtils.deleteRecursively(Path.of(STATE_DIR));
		assertThat(Files.exists(Path.of(STATE_DIR))).as("local state is gone").isFalse();
		streams.start();

		send(0, 15); // the same message again
		send(-1, 90); // an older one, in another band
		send(1, 17); // newer, same band
		send(2, 90); // newer, another band: the only one that must be announced

		List<ConsumerRecord<String, String>> records = awaitEvents(2);
		JsonNode second = json.readTree(records.get(1).value());
		assertThat(second.path("band").stringValue()).isEqualTo("RED");
		// "GREEN" here proves the state came back: a store that started empty would have said null
		assertThat(second.path("previousBand").stringValue()).isEqualTo("GREEN");
		// nothing else follows: no false GREEN after the restart
		await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(10))
				.untilAsserted(() -> assertThat(poll()).isEmpty());
	}

	private void send(int step, int co2) {
		String observedAt = T0.plusSeconds(900L * step).toString();
		kafka.send(Topics.SNAPSHOT_RECEIVED, scope, """
				{"schemaVersion": 1, "scope": "%s", "observedAt": "%s", "co2Intensity": %d, "consumption": 49970,
				 "quality": "PROVISIONAL", "estimated": false}
				""".formatted(scope, observedAt, co2)).join();
	}

	/** Every event of this test's scope seen so far, in order. */
	private final List<ConsumerRecord<String, String>> seen = new ArrayList<>();

	/** Waits until this test's scope has {@code count} events in total, and returns all of them. */
	private List<ConsumerRecord<String, String>> awaitEvents(int count) {
		await().atMost(LIMIT).until(() -> {
			seen.addAll(poll());
			return seen.size() >= count;
		});
		assertThat(seen).hasSize(count);
		return List.copyOf(seen);
	}

	private List<ConsumerRecord<String, String>> poll() {
		List<ConsumerRecord<String, String>> mine = new ArrayList<>();
		for (ConsumerRecord<String, String> record : bandEvents.poll(Duration.ofMillis(500))) {
			if (scope.equals(record.key())) {
				mine.add(record);
			}
		}
		return mine;
	}
}
