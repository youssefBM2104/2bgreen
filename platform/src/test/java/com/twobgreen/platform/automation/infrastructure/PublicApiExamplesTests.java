package com.twobgreen.platform.automation.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import tools.jackson.databind.JsonNode;

/**
 * Every example of the public contract is valid for the schema of the answer it illustrates. The list is read from
 * the spec itself, so an example added there is checked without touching this class.
 */
class PublicApiExamplesTests {

	/** One example: the response it belongs to, its schema name and the file. */
	record Example(String where, String schema, String file) {

		@Override
		public String toString() {
			return where + " -> " + file;
		}
	}

	static Stream<Example> examples() {
		List<Example> found = new ArrayList<>();
		JsonNode document = PublicApiContract.document();
		for (Map.Entry<String, JsonNode> path : document.path("paths").properties()) {
			for (Map.Entry<String, JsonNode> operation : path.getValue().properties()) {
				for (Map.Entry<String, JsonNode> response : operation.getValue().path("responses").properties()) {
					JsonNode answer = resolve(document, response.getValue());
					for (Map.Entry<String, JsonNode> media : answer.path("content").properties()) {
						String schema = refName(media.getValue().path("schema"));
						for (JsonNode example : media.getValue().path("examples")) {
							found.add(new Example(operation.getKey().toUpperCase() + " " + path.getKey() + " "
									+ response.getKey(), schema, example.path("externalValue").stringValue()));
						}
					}
				}
			}
		}
		return found.stream();
	}

	@Test
	void everyExampleFileIsUsedByTheSpec() throws IOException {
		List<String> files;
		try (Stream<Path> list = Files.list(PublicApiContract.DOCS_API.resolve("public-examples"))) {
			files = list.map(file -> "./public-examples/" + file.getFileName()).toList();
		}

		assertThat(examples().map(Example::file).distinct()).containsExactlyInAnyOrderElementsOf(files);
	}

	@ParameterizedTest
	@MethodSource("examples")
	void exampleIsValidForItsSchema(Example example) {
		assertThat(PublicApiContract.violations(example.schema(), PublicApiContract.example(example.file())))
				.isEmpty();
	}

	private static JsonNode resolve(JsonNode document, JsonNode node) {
		String ref = node.path("$ref").asString("");
		if (ref.isEmpty()) {
			return node;
		}
		JsonNode target = document;
		for (String part : ref.substring(2).split("/")) {
			target = target.path(part);
		}
		return target;
	}

	private static String refName(JsonNode schema) {
		String ref = schema.path("$ref").stringValue();
		return ref.substring(ref.lastIndexOf('/') + 1);
	}
}
