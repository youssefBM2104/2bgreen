package com.twobgreen.platform.advisory.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.twobgreen.platform.advisory.domain.GridMix;
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
		return reading(tree(json));
	}

	/**
	 * The {@code items} of a K2 range body ({@code SnapshotRange}), in the order received.
	 *
	 * @throws IllegalArgumentException if the body does not respect K2
	 */
	static List<GridReading> parseRange(String json) {
		JsonNode items = tree(json).path("items");
		if (!items.isArray()) {
			throw new IllegalArgumentException("items is required");
		}
		List<GridReading> readings = new ArrayList<>(items.size());
		for (JsonNode item : items) {
			if (!item.isObject()) {
				throw new IllegalArgumentException("an item is not a JSON object");
			}
			readings.add(reading(item));
		}
		return readings;
	}

	private static GridReading reading(JsonNode root) {
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
				instant(root, "storedAt"),
				mix(root));
	}

	/** {@code consumption} and the {@code production} object; an absent or null {@code production} knows nothing. */
	private static GridMix mix(JsonNode root) {
		JsonNode production = root.path("production");
		if (!production.isMissingNode() && !production.isNull() && !production.isObject()) {
			throw new IllegalArgumentException("production is not an object");
		}
		return new GridMix(
				integer(root, "consumption"),
				integer(production, "nuclear"),
				integer(production, "gas"),
				integer(production, "coal"),
				integer(production, "oil"),
				integer(production, "thermal"),
				integer(production, "wind"),
				integer(production, "solar"),
				integer(production, "hydro"),
				integer(production, "bioenergy"));
	}

	/** MW are whole numbers in K2: a decimal or a string is a broken contract, not a value to round. */
	private static Integer integer(JsonNode node, String field) {
		JsonNode value = node.path(field);
		if (value.isMissingNode() || value.isNull()) {
			return null;
		}
		if (!value.isIntegralNumber() || !value.canConvertToInt()) {
			throw new IllegalArgumentException(field + " is not a whole number");
		}
		return value.intValue();
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
