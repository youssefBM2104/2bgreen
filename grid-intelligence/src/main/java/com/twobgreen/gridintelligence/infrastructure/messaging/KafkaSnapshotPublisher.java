package com.twobgreen.gridintelligence.infrastructure.messaging;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.twobgreen.gridintelligence.application.SnapshotPublisher;
import com.twobgreen.gridintelligence.domain.GridSnapshot;

/**
 * Publishes on {@code grid.snapshot-received} with {@code scope} as key (K1): one scope, one partition, so its
 * readings stay in order. All messages are sent, then all acknowledgements awaited: a whole backfill does not wait
 * for each message in turn, and the producer's idempotence (Kafka default) keeps the order through retries.
 */
@Component
class KafkaSnapshotPublisher implements SnapshotPublisher {

	private static final Duration ACK_TIMEOUT = Duration.ofSeconds(30);

	private final KafkaTemplate<String, String> kafka;

	private final SnapshotMessages messages = new SnapshotMessages();

	KafkaSnapshotPublisher(KafkaTemplate<String, String> kafka) {
		this.kafka = kafka;
	}

	@Override
	public void publish(List<GridSnapshot> snapshots) {
		CompletableFuture<?>[] sends = snapshots.stream()
				.map(snapshot -> kafka.send(Topics.SNAPSHOT_RECEIVED, snapshot.scope(), messages.receivedEvent(snapshot)))
				.toArray(CompletableFuture[]::new);
		try {
			CompletableFuture.allOf(sends).get(ACK_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
		}
		catch (ExecutionException e) {
			throw new IllegalStateException("Kafka did not acknowledge every snapshot", e.getCause());
		}
		catch (TimeoutException e) {
			throw new IllegalStateException("Kafka did not acknowledge every snapshot within " + ACK_TIMEOUT, e);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("interrupted while waiting for Kafka", e);
		}
	}
}
