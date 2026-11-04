package com.twobgreen.gridintelligence;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * The schemas of the K2 contract ({@code docs/api/openapi.yaml}) as a test oracle: a body is valid if the spec says so.
 * The examples are read from {@code docs/api/examples} by path and never copied, so they cannot drift.
 */
final class OpenApiContract {

	// Maven runs from the module directory
	static final Path DOCS_API = Path.of("..", "docs", "api").toAbsolutePath().normalize();

	static final Path EXAMPLES = DOCS_API.resolve("examples");

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final JsonNode COMPONENTS = readSpec().path("components");

	// OpenAPI 3.1 schemas are JSON Schema draft 2020-12. Format assertions on, so a date-time must really be one.
	private static final SchemaRegistry REGISTRY = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12,
			builder -> builder.schemaRegistryConfig(
					SchemaRegistryConfig.builder().formatAssertionsEnabled(true).build()));

	private OpenApiContract() {
	}

	/** Messages of the schema violations, empty when {@code body} is valid for {@code components/schemas/<name>}. */
	static List<String> violations(String schemaName, JsonNode body) {
		return schema(schemaName).validate(body).stream().map(Error::getMessage).toList();
	}

	static JsonNode parse(String json) {
		return JSON.readTree(json);
	}

	static JsonNode example(String fileName) {
		return parse(read(EXAMPLES.resolve(fileName)));
	}

	/** Same value, ignoring fields that are {@code null}: K1 and K2 accept a missing value as null or as absent. */
	static Object withoutNulls(JsonNode node) {
		return stripNulls(JSON.convertValue(node, Object.class));
	}

	// The schema is looked up in the whole document: the wrapper keeps the contract's own "#/components/..." refs valid
	private static Schema schema(String name) {
		ObjectNode wrapper = JSON.createObjectNode();
		wrapper.put("$ref", "#/components/schemas/" + name);
		wrapper.set("components", COMPONENTS);
		return REGISTRY.getSchema(wrapper);
	}

	private static JsonNode readSpec() {
		return YAMLMapper.builder().build().readTree(read(DOCS_API.resolve("openapi.yaml")));
	}

	private static String read(Path file) {
		try {
			return Files.readString(file);
		}
		catch (IOException e) {
			throw new UncheckedIOException("cannot read " + file, e);
		}
	}

	@SuppressWarnings("unchecked")
	private static Object stripNulls(Object value) {
		if (value instanceof Map<?, ?> map) {
			Map<String, Object> kept = new java.util.TreeMap<>();
			((Map<String, Object>) map).forEach((k, v) -> {
				if (v != null) {
					kept.put(k, stripNulls(v));
				}
			});
			return kept;
		}
		if (value instanceof List<?> list) {
			return list.stream().map(OpenApiContract::stripNulls).toList();
		}
		return value;
	}
}
