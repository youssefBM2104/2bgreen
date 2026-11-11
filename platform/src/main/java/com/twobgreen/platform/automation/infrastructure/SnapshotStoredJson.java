package com.twobgreen.platform.automation.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.twobgreen.platform.automation.domain.GridObservation;

/**
 * JSON of contract K1 ({@code docs/contracts/events.md}) -> {@link GridObservation}. Reads only what the rules
 * need. Tolerant where K1 says so (a missing value is absent or {@code null}, unknown fields are ignored), strict on
 * the identity of the message.
 */
final class SnapshotStoredJson {

	private static final int SCHEMA_VERSION = 1;

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private SnapshotStoredJson() {
	}

	/**
	 * @throws IllegalArgumentException if the message does not respect K1
	 */
	static GridObservation parse(String json) {
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
		JsonNode version = root.path("schemaVersion");
		if (!version.isIntegralNumber() || version.intValue() != SCHEMA_VERSION) {
			throw new IllegalArgumentException("unsupported schemaVersion");
		}
		return new GridObservation(requiredText(root, "scope"), observedAt(root), co2Intensity(root));
	}

	private static String requiredText(JsonNode root, String field) {
		JsonNode node = root.path(field);
		if (!node.isString() || node.stringValue().isBlank()) {
			throw new IllegalArgumentException(field + " is required");
		}
		return node.stringValue();
	}

	private static Instant observedAt(JsonNode root) {
		try {
			return Instant.parse(requiredText(root, "observedAt"));
		}
		catch (DateTimeParseException e) {
			throw new IllegalArgumentException("observedAt is not a UTC date", e);
		}
	}

	private static BigDecimal co2Intensity(JsonNode root) {
		JsonNode node = root.path("co2Intensity");
		if (node.isMissingNode() || node.isNull()) {
			return null;
		}
		if (!node.isNumber()) {
			throw new IllegalArgumentException("co2Intensity is not a number");
		}
		return node.decimalValue();
	}
}
