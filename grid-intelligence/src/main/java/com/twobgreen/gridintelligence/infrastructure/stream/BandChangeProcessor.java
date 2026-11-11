package com.twobgreen.gridintelligence.infrastructure.stream;

import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.twobgreen.gridintelligence.application.TrackBand;
import com.twobgreen.gridintelligence.domain.BandState;
import com.twobgreen.gridintelligence.domain.GridSnapshot;
import com.twobgreen.gridintelligence.infrastructure.messaging.BandMessages;
import com.twobgreen.gridintelligence.infrastructure.messaging.InvalidSnapshotMessageException;
import com.twobgreen.gridintelligence.infrastructure.messaging.SnapshotMessages;
import com.twobgreen.gridintelligence.infrastructure.messaging.Topics;

/**
 * The stateful step of the stream (G9): for each stored snapshot, read the state of its scope, let {@link TrackBand}
 * decide, write the new state, and forward an event only when the band changed.
 * <p>
 * The store is keyed by the scope read from the message itself, not by the record key.
 */
final class BandChangeProcessor implements Processor<String, String, String, String> {

	/** Name of the state store, also part of the name of its changelog topic. */
	static final String STORE = "band-state";

	private static final Logger log = LoggerFactory.getLogger(BandChangeProcessor.class);

	private final TrackBand trackBand;

	private final SnapshotMessages snapshots;

	private final BandMessages bands;

	private ProcessorContext<String, String> context;

	private KeyValueStore<String, String> store;

	BandChangeProcessor(TrackBand trackBand, SnapshotMessages snapshots, BandMessages bands) {
		this.trackBand = trackBand;
		this.snapshots = snapshots;
		this.bands = bands;
	}

	@Override
	public void init(ProcessorContext<String, String> context) {
		this.context = context;
		this.store = context.getStateStore(STORE);
	}

	@Override
	public void process(Record<String, String> record) {
		GridSnapshot snapshot;
		try {
			snapshot = snapshots.parse(record.value());
		}
		catch (InvalidSnapshotMessageException e) {
			// Written by our own stream, so this is a bug or a foreign producer: skip, never kill the thread
			log.warn("Skipping invalid message on {}: {}", Topics.SNAPSHOT_STORED, e.getMessage());
			return;
		}
		String scope = snapshot.scope();
		BandState previous = BandStateCodec.decode(store.get(scope));

		TrackBand.Outcome outcome = trackBand.observe(scope, previous, snapshot.observedAt(), snapshot.co2Intensity());

		if (outcome.state() != null && !outcome.state().equals(previous)) {
			store.put(scope, BandStateCodec.encode(outcome.state()));
		}
		outcome.change().ifPresent(change -> {
			log.info("Band of {} is now {} (was {}) at {}", scope, change.band(), change.previousBand(),
					change.observedAt());
			context.forward(record.withKey(scope).withValue(bands.bandChanged(change)));
		});
	}
}
