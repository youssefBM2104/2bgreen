package com.twobgreen.gridintelligence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.test.context.ActiveProfiles;

import com.twobgreen.gridintelligence.application.OdreGateway;
import com.twobgreen.gridintelligence.application.PollNationalReadings;
import com.twobgreen.gridintelligence.application.SnapshotPublisher;
import com.twobgreen.gridintelligence.application.SpeedSnapshotRepository;
import com.twobgreen.gridintelligence.domain.GridSnapshot;
import com.twobgreen.gridintelligence.domain.Production;
import com.twobgreen.gridintelligence.domain.Quality;
import com.twobgreen.gridintelligence.infrastructure.messaging.SnapshotMessages;
import com.twobgreen.gridintelligence.infrastructure.messaging.Topics;

/**
 * The poller on real Kafka and TimescaleDB containers, with a simulated ODRÉ: the real gateway is covered by the
 * ODRÉ tests, here only what the poller does with its answers matters. The stream is not running, so nothing reads
 * grid.snapshot-received but this test.
 */
@Import({ TestcontainersConfiguration.class, PollerIntegrationTests.SimulatedOdreConfig.class })
@SpringBootTest(properties = {
		// The test drives the polls; the scheduled one must not run in between
		"poller.initial-delay=PT1H",
		"spring.kafka.consumer.auto-offset-reset=earliest"
})
@ActiveProfiles("poller")
class PollerIntegrationTests {

	private static final Duration LIMIT = Duration.ofSeconds(60);

	private static final Duration OVERLAP = Duration.ofHours(1);

	private static final Instant FIRST_START = Instant.parse("2026-08-31T22:00:00Z");

	private final SnapshotMessages messages = new SnapshotMessages();

	@Autowired
	SimulatedOdre odre;

	@Autowired
	SnapshotPublisher publisher;

	@Autowired
	SpeedSnapshotRepository repository;

	@Autowired
	ConsumerFactory<String, String> consumerFactory;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	ApplicationContext context;

	private Consumer<String, String> received;

	@BeforeEach
	void listenToReceivedEvents() {
		jdbc.update("DELETE FROM speed_snapshot");
		odre.asked.clear();
		received = consumerFactory.createConsumer("it-" + UUID.randomUUID(), "");
		received.subscribe(List.of(Topics.SNAPSHOT_RECEIVED));
	}

	@AfterEach
	void stopListening() {
		received.close();
	}

	@Test
	void pollerIsScheduledUnderThePollerProfile() {
		assertThat(context.containsBean("nationalPoller")).isTrue();
		assertThat(context.getBean(PollNationalReadings.class)).isNotNull();
	}

	@Test
	void oneMessagePerNewSnapshotWithTheScopeAsKey() {
		GridSnapshot first = reading("2026-10-20T14:45:00Z");
		GridSnapshot second = reading("2026-10-20T15:00:00Z");
		odre.next = List.of(first, second);

		newPoller().poll();

		List<ConsumerRecord<String, String>> records = awaitMessages(2, first, second);
		assertThat(records).extracting(ConsumerRecord::key).containsOnly("national");
		assertThat(records).extracting(record -> messages.parse(record.value())).containsExactly(first, second);
	}

	@Test
	void restartLosesNothingItOnlySendsAgain() {
		GridSnapshot stored = reading("2026-10-21T10:00:00Z");
		GridSnapshot publishedNotYetStored = reading("2026-10-21T10:15:00Z");
		repository.upsert(stored);

		// Before the restart: 10:15 is published, but the stream has not stored it yet
		odre.next = List.of(stored, publishedNotYetStored);
		newPoller().poll();
		// After the restart: a fresh poller only knows what is stored, so it asks from 10:00 again
		newPoller().poll();

		assertThat(odre.asked).containsExactly(stored.observedAt().minus(OVERLAP), stored.observedAt().minus(OVERLAP));
		// Duplicates, no gap: 10:15 went out twice
		assertThat(awaitMessages(4, stored, publishedNotYetStored))
				.extracting(record -> messages.parse(record.value()).observedAt())
				.containsExactly(stored.observedAt(), publishedNotYetStored.observedAt(),
						stored.observedAt(), publishedNotYetStored.observedAt());
	}

	/** A poller as after a (re)start: same beans, cursor not loaded yet. */
	private PollNationalReadings newPoller() {
		return new PollNationalReadings(odre, publisher, repository, OVERLAP, FIRST_START);
	}

	/** Exactly {@code count} messages about these readings, then a quiet second: no extra message either. */
	private List<ConsumerRecord<String, String>> awaitMessages(int count, GridSnapshot... readings) {
		List<Instant> wanted = Arrays.stream(readings).map(GridSnapshot::observedAt).toList();
		List<ConsumerRecord<String, String>> found = new ArrayList<>();
		await().atMost(LIMIT).until(() -> {
			collect(wanted, found);
			return found.size() >= count;
		});
		collect(wanted, found);
		assertThat(found).hasSize(count);
		return found;
	}

	private void collect(List<Instant> wanted, List<ConsumerRecord<String, String>> found) {
		for (ConsumerRecord<String, String> record : received.poll(Duration.ofMillis(500))) {
			if (wanted.contains(messages.parse(record.value()).observedAt())) {
				found.add(record);
			}
		}
	}

	private static GridSnapshot reading(String observedAt) {
		return new GridSnapshot(GridSnapshot.NATIONAL_SCOPE, Instant.parse(observedAt), BigDecimal.valueOf(29), 49970, 49950, 49650, new Production(38499, 2632, 0, 37, null, 6225, 4656, 3768, 941), 0,
				Quality.PROVISIONAL, false);
	}

	/** Answers {@link #next} to every call and records what it was asked. */
	static class SimulatedOdre implements OdreGateway {

		final List<Instant> asked = new CopyOnWriteArrayList<>();

		volatile List<GridSnapshot> next = List.of();

		@Override
		public List<GridSnapshot> nationalReadingsAfter(Instant after) {
			asked.add(after);
			return next;
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class SimulatedOdreConfig {

		// Primary: wins over the real gateway, which would call ODRÉ
		@Bean
		@Primary
		SimulatedOdre simulatedOdre() {
			return new SimulatedOdre();
		}
	}
}
