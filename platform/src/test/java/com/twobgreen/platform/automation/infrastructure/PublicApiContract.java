package com.twobgreen.platform.automation.infrastructure;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
 * The schemas of the public contract ({@code docs/api/public-openapi.yaml}, U7) as a test oracle, the same way
 * grid-intelligence checks K2. Examples are read from {@code docs/api/public-examples} by path and never copied.
 */
final class PublicApiContract {

	// Maven runs from the module directory
	static final Path DOCS_API = Path.of("..", "docs", "api").toAbsolutePath().normalize();

	static final Path SPEC = DOCS_API.resolve("public-openapi.yaml");

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final JsonNode DOCUMENT = YAMLMapper.builder().build().readTree(read(SPEC));

	// OpenAPI 3.1 schemas are JSON Schema draft 2020-12. Format assertions on, so a date-time must really be one.
	private static final SchemaRegistry REGISTRY = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12,
			builder -> builder.schemaRegistryConfig(
					SchemaRegistryConfig.builder().formatAssertionsEnabled(true).build()));

	private PublicApiContract() {
	}

	static JsonNode document() {
		return DOCUMENT;
	}

	/** Messages of the schema violations, empty when {@code json} is valid for {@code components/schemas/<name>}. */
	static List<String> violations(String schemaName, String json) {
		return schema(schemaName).validate(JSON.readTree(json)).stream().map(Error::getMessage).toList();
	}

	/** An example file, by its path relative to {@code docs/api} as the spec writes it ({@code ./public-examples/...}). */
	static String example(String relativePath) {
		return read(DOCS_API.resolve(relativePath).normalize());
	}

	// The schema is looked up in the whole document: the wrapper keeps the contract's own "#/components/..." refs valid
	private static Schema schema(String name) {
		ObjectNode wrapper = JSON.createObjectNode();
		wrapper.put("$ref", "#/components/schemas/" + name);
		wrapper.set("components", DOCUMENT.path("components"));
		return REGISTRY.getSchema(wrapper);
	}

	private static String read(Path file) {
		try {
			return Files.readString(file);
		}
		catch (IOException e) {
			throw new UncheckedIOException("cannot read " + file, e);
		}
	}
}
