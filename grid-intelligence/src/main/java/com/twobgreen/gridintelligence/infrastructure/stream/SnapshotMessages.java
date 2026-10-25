package com.twobgreen.gridintelligence.infrastructure.stream;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import com.twobgreen.gridintelligence.application.StoredSnapshot;
import com.twobgreen.gridintelligence.domain.GridSnapshot;
import com.twobgreen.gridintelligence.domain.Production;
import com.twobgreen.gridintelligence.domain.Quality;

/**
 * JSON wire format of contract K1 ({@code docs/contracts/events.md}) <-> domain.
 * Reading is tolerant where K1 says so (missing fields can be absent or {@code null}, unknown fields are ignored)
 * and strict everywhere else: a wrong type or a bad value rejects the whole message.
 */
class SnapshotMessages {

	static final int SCHEMA_VERSION = 1;

	private final JsonMapper mapper = JsonMapper.builder().build();

	GridSnapshot parse(String json) {
		if (json == null || json.isBlank()) {
			throw new InvalidSnapshotMessageException("empty message");
		}
		JsonNode root;
		try {
			root = mapper.readTree(json);
		}
		catch (JacksonException e) {
			throw new InvalidSnapshotMessageException("not valid JSON", e);
		}
		if (!root.isObject()) {
			throw new InvalidSnapshotMessageException("message is not a JSON object");
		}

		Integer schemaVersion = integer(root, "schemaVersion");
		if (schemaVersion == null || schemaVersion != SCHEMA_VERSION) {
			throw new InvalidSnapshotMessageException("unsupported schemaVersion " + schemaVersion);
		}

		JsonNode production = root.path("production");
		Production sources = new Production(
				integer(production, "nuclear"),
				integer(production, "gas"),
				integer(production, "coal"),
				integer(production, "oil"),
				integer(production, "thermal"),
				integer(production, "wind"),
				integer(production, "solar"),
				integer(production, "hydro"),
				integer(production, "bioenergy"));

		try {
			return new GridSnapshot(
					requiredText(root, "scope"),
					instant(root, "observedAt"),
					decimal(root, "co2Intensity"),
					integer(root, "consumption"),
					integer(root, "consumptionForecastDay"),
					integer(root, "consumptionForecastDayBefore"),
					sources,
					integer(root, "pumping"),
					quality(root),
					bool(root, "estimated"));
		}
		catch (IllegalArgumentException e) {
			throw new InvalidSnapshotMessageException(e.getMessage(), e);
		}
	}

	/** The {@code grid.snapshot-stored} payload: the received one plus {@code storedAt}. */
	String storedEvent(StoredSnapshot stored) {
		GridSnapshot snapshot = stored.snapshot();
		Production production = snapshot.production();

		ObjectNode root = mapper.createObjectNode();
		root.put("schemaVersion", SCHEMA_VERSION);
		root.put("scope", snapshot.scope());
		root.put("observedAt", snapshot.observedAt().toString());
		root.put("co2Intensity", snapshot.co2Intensity());
		root.put("consumption", snapshot.consumption());
		root.put("consumptionForecastDay", snapshot.consumptionForecastDay());
		root.put("consumptionForecastDayBefore", snapshot.consumptionForecastDayBefore());
		ObjectNode sources = root.putObject("production");
		sources.put("nuclear", production.nuclear());
		sources.put("gas", production.gas());
		sources.put("coal", production.coal());
		sources.put("oil", production.oil());
		sources.put("thermal", production.thermal());
		sources.put("wind", production.wind());
		sources.put("solar", production.solar());
		sources.put("hydro", production.hydro());
		sources.put("bioenergy", production.bioenergy());
		root.put("pumping", snapshot.pumping());
		root.put("quality", snapshot.quality().name());
		root.put("estimated", snapshot.estimated());
		root.put("storedAt", stored.storedAt().toString());
		return mapper.writeValueAsString(root);
	}

	private static boolean missing(JsonNode node) {
		return node.isMissingNode() || node.isNull();
	}

	private static Integer integer(JsonNode parent, String field) {
		JsonNode node = parent.path(field);
		if (missing(node)) {
			return null;
		}
		if (!node.isNumber()) {
			throw new InvalidSnapshotMessageException(field + " must be a number");
		}
		try {
			return node.decimalValue().intValueExact();
		}
		catch (ArithmeticException e) {
			throw new InvalidSnapshotMessageException(field + " must be a whole number in the int range", e);
		}
	}

	private static BigDecimal decimal(JsonNode parent, String field) {
		JsonNode node = parent.path(field);
		if (missing(node)) {
			return null;
		}
		if (!node.isNumber()) {
			throw new InvalidSnapshotMessageException(field + " must be a number");
		}
		return node.decimalValue();
	}

	private static boolean bool(JsonNode parent, String field) {
		JsonNode node = parent.path(field);
		if (missing(node)) {
			return false;
		}
		if (!node.isBoolean()) {
			throw new InvalidSnapshotMessageException(field + " must be a boolean");
		}
		return node.booleanValue();
	}

	private static String requiredText(JsonNode parent, String field) {
		JsonNode node = parent.path(field);
		if (!node.isString()) {
			throw new InvalidSnapshotMessageException(field + " is required and must be a string");
		}
		return node.stringValue();
	}

	private static Instant instant(JsonNode parent, String field) {
		String text = requiredText(parent, field);
		try {
			return OffsetDateTime.parse(text).toInstant();
		}
		catch (DateTimeParseException e) {
			throw new InvalidSnapshotMessageException(field + " is not an ISO-8601 date-time: " + text, e);
		}
	}

	private static Quality quality(JsonNode parent) {
		String text = requiredText(parent, "quality");
		try {
			return Quality.valueOf(text);
		}
		catch (IllegalArgumentException e) {
			throw new InvalidSnapshotMessageException("unknown quality " + text, e);
		}
	}
}
