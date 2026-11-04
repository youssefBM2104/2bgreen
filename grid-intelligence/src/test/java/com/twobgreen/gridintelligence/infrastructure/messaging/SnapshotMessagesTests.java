package com.twobgreen.gridintelligence.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.twobgreen.gridintelligence.application.StoredSnapshot;
import com.twobgreen.gridintelligence.domain.GridSnapshot;
import com.twobgreen.gridintelligence.domain.Production;
import com.twobgreen.gridintelligence.domain.Quality;

class SnapshotMessagesTests {

	private final SnapshotMessages messages = new SnapshotMessages();

	private final JsonMapper json = JsonMapper.builder().build();

	@Test
	void parsesAFullMessage() {
		GridSnapshot snapshot = messages.parse(SnapshotJson.NATIONAL_MEASURED);

		assertThat(snapshot.scope()).isEqualTo("national");
		assertThat(snapshot.observedAt()).isEqualTo(Instant.parse("2026-10-20T14:45:00Z"));
		assertThat(snapshot.co2Intensity()).isEqualByComparingTo(BigDecimal.valueOf(29));
		assertThat(snapshot.consumption()).isEqualTo(49970);
		assertThat(snapshot.consumptionForecastDay()).isEqualTo(49950);
		assertThat(snapshot.consumptionForecastDayBefore()).isEqualTo(49650);
		assertThat(snapshot.production()).isEqualTo(new Production(38499, 2632, 0, 37, null, 6225, 4656, 3768, 941));
		assertThat(snapshot.pumping()).isZero();
		assertThat(snapshot.quality()).isEqualTo(Quality.PROVISIONAL);
		assertThat(snapshot.estimated()).isFalse();
	}

	@Test
	void missingValuesAreNullWhetherAbsentOrExplicitNull() {
		GridSnapshot snapshot = messages.parse(SnapshotJson.REGION_MISSING_VALUES);

		assertThat(snapshot.co2Intensity()).isNull();
		assertThat(snapshot.consumptionForecastDay()).isNull();
		assertThat(snapshot.consumptionForecastDayBefore()).isNull();
		assertThat(snapshot.pumping()).isNull();
		assertThat(snapshot.production()).isEqualTo(new Production(1257, null, null, null, 1, 376, null, null, null));
		assertThat(snapshot.estimated()).isTrue();
	}

	@Test
	void messageWithOnlyTheRequiredFieldsIsValid() {
		GridSnapshot snapshot = messages.parse("""
				{"schemaVersion": 1, "scope": "national", "observedAt": "2026-10-20T14:45:00Z", "quality": "PROVISIONAL"}
				""");

		assertThat(snapshot.production()).isEqualTo(Production.NONE);
		assertThat(snapshot.consumption()).isNull();
	}

	@Test
	void unknownFieldsAreIgnored() {
		GridSnapshot snapshot = messages.parse("""
				{"schemaVersion": 1, "scope": "national", "observedAt": "2026-10-20T14:45:00Z",
				 "quality": "PROVISIONAL", "somethingNew": {"a": 1}}
				""");

		assertThat(snapshot.scope()).isEqualTo("national");
	}

	@Test
	void wholeNumberWithDecimalPointIsAccepted() {
		GridSnapshot snapshot = messages.parse("""
				{"schemaVersion": 1, "scope": "national", "observedAt": "2026-10-20T14:45:00Z",
				 "quality": "PROVISIONAL", "consumption": 49970.0}
				""");

		assertThat(snapshot.consumption()).isEqualTo(49970);
	}

	@Test
	void rejectsMessagesThatBreakTheContract() {
		String valid = "\"schemaVersion\": 1, \"scope\": \"national\", \"observedAt\": \"2026-10-20T14:45:00Z\", "
				+ "\"quality\": \"PROVISIONAL\"";

		assertInvalid(null);
		assertInvalid("");
		assertInvalid("not json");
		assertInvalid("[1, 2]");
		assertInvalid("{" + valid.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2") + "}");
		assertInvalid("{" + valid.replace("\"schemaVersion\": 1, ", "") + "}");
		assertInvalid("{" + valid.replace("\"scope\": \"national\", ", "") + "}");
		assertInvalid("{" + valid.replace("\"scope\": \"national\"", "\"scope\": \" \"") + "}");
		assertInvalid("{" + valid.replace("\"observedAt\": \"2026-10-20T14:45:00Z\", ", "") + "}");
		assertInvalid("{" + valid.replace("2026-10-20T14:45:00Z", "yesterday") + "}");
		assertInvalid("{" + valid.replace("\"quality\": \"PROVISIONAL\"", "\"quality\": \"GUESSED\"") + "}");
		assertInvalid("{" + valid.replace(", \"quality\": \"PROVISIONAL\"", "") + "}");
		assertInvalid("{" + valid + ", \"consumption\": \"49970\"}");
		assertInvalid("{" + valid + ", \"consumption\": 49970.5}");
		assertInvalid("{" + valid + ", \"production\": {\"wind\": \"ND\"}}");
		assertInvalid("{" + valid + ", \"estimated\": \"yes\"}");
	}

	@Test
	void receivedEventParsesBackToTheSameSnapshot() {
		// What the poller writes is what the stream reads
		for (String message : new String[] { SnapshotJson.NATIONAL_MEASURED, SnapshotJson.REGION_MISSING_VALUES }) {
			GridSnapshot snapshot = messages.parse(message);

			assertThat(messages.parse(messages.receivedEvent(snapshot))).isEqualTo(snapshot);
		}
	}

	@Test
	void receivedEventHasNoStoredAt() {
		JsonNode event = json.readTree(messages.receivedEvent(messages.parse(SnapshotJson.NATIONAL_MEASURED)));

		assertThat(event.has("storedAt")).isFalse();
		assertThat(event.path("schemaVersion").intValue()).isEqualTo(1);
		assertThat(event.path("quality").stringValue()).isEqualTo("PROVISIONAL");
	}

	@Test
	void storedEventIsTheReceivedPayloadPlusStoredAt() {
		GridSnapshot snapshot = messages.parse(SnapshotJson.NATIONAL_MEASURED);

		JsonNode event = json.readTree(messages.storedEvent(
				new StoredSnapshot(snapshot, Instant.parse("2026-10-20T14:50:03.250Z"))));

		assertThat(event.path("schemaVersion").intValue()).isEqualTo(1);
		assertThat(event.path("scope").stringValue()).isEqualTo("national");
		assertThat(event.path("observedAt").stringValue()).isEqualTo("2026-10-20T14:45:00Z");
		assertThat(event.path("storedAt").stringValue()).isEqualTo("2026-10-20T14:50:03.250Z");
		assertThat(event.path("quality").stringValue()).isEqualTo("PROVISIONAL");
		assertThat(event.path("consumption").intValue()).isEqualTo(49970);
		assertThat(event.path("production").path("nuclear").intValue()).isEqualTo(38499);
		assertThat(event.path("pumping").intValue()).isZero();
	}

	@Test
	void storedEventKeepsMissingValuesNullNeverZero() {
		GridSnapshot snapshot = messages.parse(SnapshotJson.REGION_MISSING_VALUES);

		JsonNode event = json.readTree(messages.storedEvent(
				new StoredSnapshot(snapshot, Instant.parse("2026-10-20T14:50:03Z"))));

		assertThat(event.path("co2Intensity").isNull()).isTrue();
		assertThat(event.path("consumptionForecastDay").isNull()).isTrue();
		assertThat(event.path("pumping").isNull()).isTrue();
		assertThat(event.path("production").path("hydro").isNull()).isTrue();
		assertThat(event.path("production").path("thermal").intValue()).isEqualTo(1);
		assertThat(event.path("estimated").booleanValue()).isTrue();
	}

	private void assertInvalid(String message) {
		assertThatThrownBy(() -> messages.parse(message))
				.as("message: %s", message)
				.isInstanceOf(InvalidSnapshotMessageException.class);
	}
}
