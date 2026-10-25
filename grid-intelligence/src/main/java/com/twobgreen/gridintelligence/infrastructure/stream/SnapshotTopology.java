package com.twobgreen.gridintelligence.infrastructure.stream;

import java.util.List;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.twobgreen.gridintelligence.application.StoreSnapshot;
import com.twobgreen.gridintelligence.domain.GridSnapshot;
import com.twobgreen.gridintelligence.domain.Quality;
import com.twobgreen.gridintelligence.infrastructure.messaging.Topics;

/**
 * Wiring only: grid.snapshot-received -> parse -> store -> grid.snapshot-stored.
 * The write to the database comes before the publication, so a crash in between replays the message
 * (at-least-once) and the idempotent upsert absorbs it.
 */
final class SnapshotTopology {

	private static final Logger log = LoggerFactory.getLogger(SnapshotTopology.class);

	private SnapshotTopology() {
	}

	static KStream<String, String> build(StreamsBuilder builder, StoreSnapshot store, SnapshotMessages messages) {
		KStream<String, String> stored = builder
				.stream(Topics.SNAPSHOT_RECEIVED, Consumed.with(Serdes.String(), Serdes.String()))
				.flatMapValues(json -> parseOrSkip(json, messages))
				.mapValues(store::store)
				.map((key, result) -> KeyValue.pair(result.snapshot().scope(), messages.storedEvent(result)));
		stored.to(Topics.SNAPSHOT_STORED, Produced.with(Serdes.String(), Serdes.String()));
		return stored;
	}

	/** A bad message is dropped here, it must never reach the store step and kill the stream thread. */
	private static List<GridSnapshot> parseOrSkip(String json, SnapshotMessages messages) {
		try {
			GridSnapshot snapshot = messages.parse(json);
			if (snapshot.quality() != Quality.PROVISIONAL) {
				log.warn("Skipping snapshot of {} at {}: quality is {}, only PROVISIONAL is expected on {}",
						snapshot.scope(), snapshot.observedAt(), snapshot.quality(), Topics.SNAPSHOT_RECEIVED);
				return List.of();
			}
			return List.of(snapshot);
		}
		catch (InvalidSnapshotMessageException e) {
			log.warn("Skipping invalid message on {}: {}", Topics.SNAPSHOT_RECEIVED, e.getMessage());
			return List.of();
		}
	}
}
