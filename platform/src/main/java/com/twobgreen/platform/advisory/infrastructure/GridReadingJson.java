package com.twobgreen.platform.advisory.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.twobgreen.platform.advisory.domain.GridQuality;
import com.twobgreen.platform.advisory.domain.GridReading;

/**
 * JSON of contract K2 ({@code docs/api/openapi.yaml}) -> {@link GridReading}. Tolerant where K2 says so (a missing
 * value can be absent or {@code null}, unknown fields are ignored) and strict everywhere else.
 */
final class GridReadingJson {

	private static final int SCHEMA_VERSION = 1;

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private GridReadingJson() {
	}

	/**
	 * @throws IllegalArgumentException if the body does not respect K2
	 */
	static GridReading parse(String json) {
		JsonNode root = tree(json);
		JsonNode version = root.path("schemaVersion");
		if (!version.isIntegralNumber() || version.intValue() != SCHEMA_VERSION) {
			throw new IllegalArgumentException("unsupported schemaVersion");
		}
		JsonNode estimated = root.path("estimated");
		if (!estimated.isBoolean()) {
			throw new IllegalArgumentException("estimated is required");
		}
		return new GridReading(
				requiredText(root, "scope"),
				instant(root, "observedAt"),
				decimal(root, "co2Intensity"),
				quality(root),
				estimated.booleanValue(),
				instant(root, "storedAt"));
	}

	/** The stable {@code code} of a K2 error body, null if the body has none. */
	static String errorCode(String json) {
		try {
			JsonNode code = tree(json).path("code");
			return code.isString() ? code.stringValue() : null;
		}
		catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static JsonNode tree(String json) {
		JsonNode root;
		try {
			root = MAPPER.readTree(json);
		}
		catch (JacksonException e) {
			throw new IllegalArgumentException("not valid JSON", e);
		}
		if (root == null || !root.isObject()) {
			throw new IllegalArgumentException("not a JSON object");
		}
		return root;
	}

	private static String requiredText(JsonNode root, String field) {
		JsonNode node = root.path(field);
		if (!node.isString() || node.stringValue().isBlank()) {
			throw new IllegalArgumentException(field + " is required");
		}
		return node.stringValue();
	}

	private static Instant instant(JsonNode root, String field) {
		try {
			return Instant.parse(requiredText(root, field));
		}
		catch (DateTimeParseException e) {
			throw new IllegalArgumentException(field + " is not a UTC date", e);
		}
	}

	private static BigDecimal decimal(JsonNode root, String field) {
		JsonNode node = root.path(field);
		if (node.isMissingNode() || node.isNull()) {
			return null;
		}
		if (!node.isNumber()) {
			throw new IllegalArgumentException(field + " is not a number");
		}
		return node.decimalValue();
	}

	private static GridQuality quality(JsonNode root) {
		return GridQuality.valueOf(requiredText(root, "quality"));
	}
}
