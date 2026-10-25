package com.twobgreen.gridintelligence.infrastructure.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.KeyValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import com.twobgreen.gridintelligence.application.SpeedSnapshotRepository;
import com.twobgreen.gridintelligence.application.StoreSnapshot;
import com.twobgreen.gridintelligence.domain.GridSnapshot;
import com.twobgreen.gridintelligence.infrastructure.messaging.Topics;

/** Topology logic without Kafka or a database: the repository is an in-memory fake keyed like the real table. */
class SnapshotTopologyTests {

	private final FakeRepository repository = new FakeRepository();

	private final JsonMapper json = JsonMapper.builder().build();

	private TopologyTestDriver driver;

	private TestInputTopic<String, String> received;

	private TestOutputTopic<String, String> stored;

	@BeforeEach
	void startTopology() {
		StreamsBuilder builder = new StreamsBuilder();
		SnapshotTopology.build(builder, new StoreSnapshot(repository), new SnapshotMessages());
		Properties properties = new Properties();
		properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "grid-stream-test");
		properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:9092");
		driver = new TopologyTestDriver(builder.build(), properties);
		received = driver.createInputTopic(Topics.SNAPSHOT_RECEIVED, new StringSerializer(), new StringSerializer());
		stored = driver.createOutputTopic(Topics.SNAPSHOT_STORED, new StringDeserializer(), new StringDeserializer());
	}

	@AfterEach
	void stopTopology() {
		driver.close();
	}

	@Test
	void storesTheSnapshotAndPublishesTheStoredEventKeyedByScope() {
		received.pipeInput("national", SnapshotJson.NATIONAL_MEASURED);

		assertThat(repository.rows).hasSize(1);
		List<KeyValue<String, String>> events = stored.readKeyValuesToList();
		assertThat(events).hasSize(1);
		assertThat(events.get(0).key).isEqualTo("national");
		assertThat(json.readTree(events.get(0).value).path("storedAt").stringValue()).isEqualTo("2026-10-20T15:00:00Z");
	}

	@Test
	void outputKeyIsTheScopeEvenIfTheInputKeyIsMissing() {
		received.pipeInput(null, SnapshotJson.NATIONAL_MEASURED);

		assertThat(stored.readKeyValue().key).isEqualTo("national");
	}

	@Test
	void replayingTheSameMessageGivesTheSameState() {
		received.pipeInput("national", SnapshotJson.NATIONAL_MEASURED);
		GridSnapshot afterFirst = repository.rows.values().iterator().next();

		received.pipeInput("national", SnapshotJson.NATIONAL_MEASURED);

		assertThat(repository.upserts).isEqualTo(2);
		assertThat(repository.rows).hasSize(1);
		assertThat(repository.rows.values()).containsExactly(afterFirst);
	}

	@Test
	void messageWithMissingValuesIsStoredWithNulls() {
		received.pipeInput("region:76", SnapshotJson.REGION_MISSING_VALUES);

		GridSnapshot row = repository.rows.values().iterator().next();
		assertThat(row.scope()).isEqualTo("region:76");
		assertThat(row.co2Intensity()).isNull();
		assertThat(row.production().hydro()).isNull();
		assertThat(json.readTree(stored.readValue()).path("production").path("hydro").isNull()).isTrue();
	}

	@Test
	void invalidMessagesAreSkippedAndTheStreamKeepsGoing() {
		received.pipeInput("national", "not json");
		received.pipeInput("national", "{\"schemaVersion\": 7}");
		received.pipeInput("national", SnapshotJson.NATIONAL_MEASURED.replace("PROVISIONAL", "DEFINITIVE"));
		received.pipeInput("national", SnapshotJson.NATIONAL_MEASURED);

		assertThat(repository.upserts).isEqualTo(1);
		assertThat(stored.readKeyValuesToList()).hasSize(1);
	}

	/** Same identity as the table: (scope, observed_at). */
	private static class FakeRepository implements SpeedSnapshotRepository {

		final Map<String, GridSnapshot> rows = new HashMap<>();

		final List<GridSnapshot> history = new ArrayList<>();

		int upserts;

		@Override
		public Instant upsert(GridSnapshot snapshot) {
			upserts++;
			history.add(snapshot);
			rows.put(snapshot.scope() + "@" + snapshot.observedAt(), snapshot);
			return Instant.parse("2026-10-20T15:00:00Z");
		}
	}
}
