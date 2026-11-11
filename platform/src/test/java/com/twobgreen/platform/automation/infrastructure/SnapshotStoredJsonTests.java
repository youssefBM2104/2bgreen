package com.twobgreen.platform.automation.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.twobgreen.platform.automation.domain.GridObservation;

class SnapshotStoredJsonTests {

	@Test
	void readsWhatTheStreamWrites() throws IOException {
		// Written by the K1 codec of grid-intelligence, storedAt included. Read by path, never copied
		String example = Files.readString(Path.of("..", "docs", "api", "examples", "latest-national.json"));

		assertThat(SnapshotStoredJson.parse(example)).isEqualTo(new GridObservation("national",
				Instant.parse("2026-10-20T14:45:00Z"), new BigDecimal("29")));
	}

	@Test
	void missingCo2IsNullWhetherAbsentOrNull() {
		assertThat(SnapshotStoredJson.parse("""
				{"schemaVersion": 1, "scope": "region:32", "observedAt": "2026-10-20T14:45:00Z"}
				""").co2Intensity()).isNull();
		assertThat(SnapshotStoredJson.parse("""
				{"schemaVersion": 1, "scope": "region:32", "observedAt": "2026-10-20T14:45:00Z", "co2Intensity": null}
				""").co2Intensity()).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"",
			"not json",
			"[1, 2]",
			"{\"scope\": \"national\", \"observedAt\": \"2026-10-20T14:45:00Z\"}",
			"{\"schemaVersion\": 2, \"scope\": \"national\", \"observedAt\": \"2026-10-20T14:45:00Z\"}",
			"{\"schemaVersion\": 1, \"observedAt\": \"2026-10-20T14:45:00Z\"}",
			"{\"schemaVersion\": 1, \"scope\": \"national\"}",
			"{\"schemaVersion\": 1, \"scope\": \"national\", \"observedAt\": \"20/10/2026 14:45\"}",
			"{\"schemaVersion\": 1, \"scope\": \"national\", \"observedAt\": \"2026-10-20T14:45:00Z\", \"co2Intensity\": \"29\"}" })
	void messageOutsideTheContractIsRefused(String message) {
		assertThatThrownBy(() -> SnapshotStoredJson.parse(message)).isInstanceOf(IllegalArgumentException.class);
	}
}
