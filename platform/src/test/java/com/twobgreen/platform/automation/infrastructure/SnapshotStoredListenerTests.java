package com.twobgreen.platform.automation.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.kafka.KafkaContainer;

import com.twobgreen.platform.TestcontainersConfiguration;
import com.twobgreen.platform.automation.domain.AlertRule;
import com.twobgreen.platform.automation.domain.AlertRuleRepository;
import com.twobgreen.platform.automation.domain.RuleState;
import com.twobgreen.platform.automation.domain.RuleStateRepository;
import com.twobgreen.platform.automation.domain.RuleStatus;
import com.twobgreen.platform.automation.domain.RuleSubject;
import com.twobgreen.platform.delivery.domain.NotificationRepository;

/** From a message on the real topic to the notification, with the engine as the application configures it. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SnapshotStoredListenerTests {

	private final String user = UUID.randomUUID().toString();

	private final String scope = "test:" + UUID.randomUUID();

	@Autowired
	private KafkaContainer kafka;

	@Autowired
	private AlertRuleRepository alertRules;

	@Autowired
	private RuleStateRepository states;

	@Autowired
	private NotificationRepository notifications;

	@Test
	void storedSnapshotNotifiesOnceEvenWhenKafkaDeliversItTwice() {
		Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		Instant earlier = now.minus(Duration.ofMinutes(15));
		AlertRule rule = alertRules.save(new AlertRule(null, user, scope, 30, true, now, now));

		// Same key, so same partition: the four messages are read in this order
		send(message(earlier, 20));
		send(message(earlier, 20));
		send("not a snapshot");
		send(message(now, 45));

		// The last message is evaluated: everything before it was read, the invalid one did not block the topic
		await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(
				states.findBySubjectTypeAndSubjectId(RuleSubject.ALERT_RULE, rule.id()))
				.get()
				.extracting(RuleState::state, RuleState::lastObservedAt)
				.containsExactly(RuleStatus.ARMED.name(), now));
		assertThat(notifications.findByUserIdOrderByCreatedAtDesc(user)).hasSize(1);
	}

	/** A producer like the one of the stream: text key (the scope) and JSON text. */
	private void send(String message) {
		Map<String, Object> config = Map.of(
				ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
				ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
				ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		try (KafkaProducer<String, String> producer = new KafkaProducer<>(config)) {
			producer.send(new ProducerRecord<>(SnapshotStoredListener.TOPIC, scope, message));
		}
	}

	private String message(Instant observedAt, int co2) {
		return """
				{"schemaVersion": 1, "scope": "%s", "observedAt": "%s", "co2Intensity": %d, "consumption": 49970,
				"quality": "PROVISIONAL", "estimated": false, "storedAt": "%s"}
				""".formatted(scope, observedAt, co2, observedAt.plusSeconds(60));
	}
}
