package com.twobgreen.gridintelligence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.twobgreen.gridintelligence.infrastructure.messaging.Topics;

/**
 * The whole speed layer on real Kafka and TimescaleDB containers: grid.snapshot-received -> speed_snapshot ->
 * grid.snapshot-stored. Values are those of the committed fixture national-tr-measured.json.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
		"spring.kafka.streams.properties.commit.interval.ms=100",
		// Streams does not leave the group on stop: after a restart the broker waits for the old session to expire
		// (45 s by default) before reassigning partitions. 6 s is the broker minimum, it keeps the restart test fast.
		"spring.kafka.streams.properties.consumer.session.timeout.ms=6000",
		"spring.kafka.consumer.auto-offset-reset=earliest"
})
@ActiveProfiles("stream")
class SnapshotStreamIntegrationTests {

	private static final Duration LIMIT = Duration.ofSeconds(60);

	private static final String MESSAGE = """
			{
			  "schemaVersion": 1, "scope": "national", "observedAt": "%s",
			  "co2Intensity": 29, "consumption": 49970,
			  "consumptionForecastDay": 49950, "consumptionForecastDayBefore": 49650,
			  "production": {"nuclear": 38499, "gas": 2632, "coal": 0, "oil": 37, "thermal": null,
			                 "wind": 6225, "solar": 4656, "hydro": 3768, "bioenergy": 941},
			  "pumping": 0, "quality": "PROVISIONAL", "estimated": false
			}
			""";

	private final JsonMapper json = JsonMapper.builder().build();

	@Autowired
	KafkaTemplate<String, String> kafka;

	@Autowired
	ConsumerFactory<String, String> consumerFactory;

	@Autowired
	StreamsBuilderFactoryBean streams;

	@Autowired
	JdbcTemplate jdbc;

	private Consumer<String, String> storedEvents;

	@BeforeEach
	void listenToStoredEvents() {
		jdbc.update("DELETE FROM speed_snapshot");
		storedEvents = consumerFactory.createConsumer("it-" + UUID.randomUUID(), "");
		storedEvents.subscribe(List.of(Topics.SNAPSHOT_STORED));
	}

	@AfterEach
	void stopListening() {
		storedEvents.close();
		if (!streams.isRunning()) {
			streams.start();
		}
	}

	@Test
	void publishedMessageIsStoredAndAnnouncedWithinTheMinute() {
		String observedAt = "2026-10-20T14:45:00Z";

		send(observedAt);

		await().atMost(LIMIT).untilAsserted(() -> assertThat(rows(observedAt)).isEqualTo(1));
		assertThat(jdbc.queryForObject("SELECT consumption FROM speed_snapshot WHERE observed_at = ?::timestamptz",
				Integer.class, observedAt)).isEqualTo(49970);

		JsonNode event = awaitStoredEvent(observedAt);
		assertThat(event.path("scope").stringValue()).isEqualTo("national");
		assertThat(event.path("production").path("nuclear").intValue()).isEqualTo(38499);
		assertThat(Instant.parse(event.path("storedAt").stringValue())).isEqualTo(storedAt(observedAt));
	}

	@Test
	void replayingTheSameMessageAddsNoRow() {
		String observedAt = "2026-10-20T15:00:00Z";

		send(observedAt);
		send(observedAt);
		send(observedAt);

		await().atMost(LIMIT).until(() -> rows(observedAt) == 1);
		// the replays were consumed too: a marker sent after them is stored, so they are behind us
		send("2026-10-20T15:15:00Z");
		await().atMost(LIMIT).until(() -> rows("2026-10-20T15:15:00Z") == 1);
		assertThat(rows(observedAt)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM speed_snapshot", Integer.class)).isEqualTo(2);
	}

	@Test
	void resumesFromTheCommittedOffsetsAfterARestart() {
		String before = "2026-10-20T15:30:00Z";
		String during = "2026-10-20T15:45:00Z";

		send(before);
		await().atMost(LIMIT).until(() -> rows(before) == 1);
		Instant storedAtBeforeRestart = storedAt(before);

		streams.stop();
		send(during);
		assertThat(rows(during)).isZero();
		streams.start();

		await().atMost(LIMIT).until(() -> rows(during) == 1);
		// a message that had been consumed again would have been upserted again, which refreshes stored_at
		assertThat(storedAt(before)).isEqualTo(storedAtBeforeRestart);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM speed_snapshot", Integer.class)).isEqualTo(2);
	}

	private void send(String observedAt) {
		kafka.send(Topics.SNAPSHOT_RECEIVED, "national", MESSAGE.formatted(observedAt)).join();
	}

	private int rows(String observedAt) {
		return jdbc.queryForObject("SELECT count(*) FROM speed_snapshot WHERE observed_at = ?::timestamptz",
				Integer.class, observedAt);
	}

	private Instant storedAt(String observedAt) {
		return jdbc.queryForObject("SELECT stored_at FROM speed_snapshot WHERE observed_at = ?::timestamptz",
				Timestamp.class, observedAt).toInstant();
	}

	private JsonNode awaitStoredEvent(String observedAt) {
		JsonNode[] found = new JsonNode[1];
		await().atMost(LIMIT).until(() -> {
			for (ConsumerRecord<String, String> record : storedEvents.poll(Duration.ofMillis(500))) {
				JsonNode event = json.readTree(record.value());
				if (observedAt.equals(event.path("observedAt").stringValue())) {
					assertThat(record.key()).isEqualTo("national");
					found[0] = event;
				}
			}
			return found[0] != null;
		});
		return found[0];
	}
}
