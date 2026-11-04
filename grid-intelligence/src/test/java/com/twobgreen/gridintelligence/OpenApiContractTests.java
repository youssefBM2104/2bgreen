package com.twobgreen.gridintelligence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;

/**
 * The examples of docs/api are the fixtures of the frontend, of the platform stub and of the API tests. They must say
 * what the spec says, and the validator itself must not be trivially satisfied.
 */
class OpenApiContractTests {

	static Stream<Path> examples() throws IOException {
		try (Stream<Path> files = Files.list(OpenApiContract.EXAMPLES)) {
			return files.filter(file -> file.toString().endsWith(".json")).sorted().toList().stream();
		}
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("examples")
	void everyExampleValidatesAgainstItsSchema(Path example) {
		String name = example.getFileName().toString();
		String schema = name.startsWith("error-") ? "Problem" : name.startsWith("range-") ? "SnapshotRange" : "Snapshot";

		assertThat(OpenApiContract.violations(schema, OpenApiContract.example(name))).as(name).isEmpty();
	}

	@Test
	void theValidatorRejectsWhatTheSpecForbids() {
		assertThat(OpenApiContract.violations("Snapshot", OpenApiContract.parse("{}"))).isNotEmpty();
		assertThat(OpenApiContract.violations("Snapshot", changed("latest-national.json", "\"PROVISIONAL\"", "\"GUESSED\"")))
				.as("unknown quality").isNotEmpty();
		assertThat(OpenApiContract.violations("Snapshot", changed("latest-national.json", "\"consumption\":49970", "\"consumption\":\"49970\"")))
				.as("a number written as a string").isNotEmpty();
		assertThat(OpenApiContract.violations("Snapshot", changed("latest-national.json", "\"pumping\":0", "\"pumping\":600")))
				.as("positive pumping").isNotEmpty();
		assertThat(OpenApiContract.violations("Snapshot", changed("latest-national.json", "2026-10-20T15:03:12Z", "yesterday")))
				.as("a date that is not a date-time").isNotEmpty();
		assertThat(OpenApiContract.violations("Problem", changed("error-no-data.json", "NO_DATA", "SOMETHING_ELSE")))
				.as("unknown error code").isNotEmpty();
	}

	// The example with one piece of text replaced; the piece must exist, or the check would prove nothing
	private static JsonNode changed(String example, String from, String to) {
		String json = OpenApiContract.example(example).toString();
		assertThat(json).as("%s must contain %s", example, from).contains(from);
		return OpenApiContract.parse(json.replace(from, to));
	}
}
