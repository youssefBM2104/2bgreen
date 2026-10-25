package com.twobgreen.gridintelligence.infrastructure.odre;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.twobgreen.gridintelligence.application.OdreException;
import com.twobgreen.gridintelligence.application.OdreException.Reason;

/**
 * Decodes {@code records} responses into DTOs. Strict on the structure (envelope, {@code date_heure}), lenient on
 * the measures: they are kept as raw text whatever their JSON type, see {@link NationalRealtimeRecord}.
 */
class OdreResponses {

	private final JsonMapper mapper = JsonMapper.builder().build();

	RecordsPage<NationalRealtimeRecord> nationalRealtime(String body) {
		return page(body, OdreResponses::nationalRealtimeRow);
	}

	private <T> RecordsPage<T> page(String body, Function<JsonNode, T> row) {
		if (body == null || body.isBlank()) {
			throw invalid("empty body");
		}
		JsonNode root;
		try {
			root = mapper.readTree(body);
		}
		catch (JacksonException e) {
			throw new OdreException(Reason.INVALID_RESPONSE, "body is not valid JSON", e);
		}
		JsonNode totalCount = root.path("total_count");
		if (!totalCount.isIntegralNumber()) {
			throw invalid("total_count is missing or not a whole number");
		}
		JsonNode results = root.path("results");
		if (!results.isArray()) {
			throw invalid("results is missing or not an array");
		}
		List<T> rows = new ArrayList<>(results.size());
		for (JsonNode node : results) {
			if (!node.isObject()) {
				throw invalid("a row of results is not a JSON object");
			}
			rows.add(row.apply(node));
		}
		return new RecordsPage<>(totalCount.longValue(), rows);
	}

	private static NationalRealtimeRecord nationalRealtimeRow(JsonNode row) {
		return new NationalRealtimeRecord(
				dateHeure(row),
				raw(row, "consommation"),
				raw(row, "taux_co2"),
				raw(row, "prevision_j"),
				raw(row, "prevision_j1"),
				raw(row, "nucleaire"),
				raw(row, "gaz"),
				raw(row, "charbon"),
				raw(row, "fioul"),
				raw(row, "eolien"),
				raw(row, "solaire"),
				raw(row, "hydraulique"),
				raw(row, "bioenergies"),
				raw(row, "pompage"));
	}

	/** Required: rows are ordered and paged on it, a row without it is unusable. */
	private static Instant dateHeure(JsonNode row) {
		JsonNode node = row.path("date_heure");
		if (!node.isString()) {
			throw invalid("date_heure is missing or not a string");
		}
		try {
			return OffsetDateTime.parse(node.stringValue()).toInstant();
		}
		catch (DateTimeParseException e) {
			throw new OdreException(Reason.INVALID_RESPONSE, "date_heure is not an ISO-8601 date-time", e);
		}
	}

	/** The value as ODRÉ wrote it: {@code 29} and {@code "29"} both give {@code "29"}; null or absent give null. */
	private static String raw(JsonNode row, String field) {
		JsonNode node = row.path(field);
		if (node.isMissingNode() || node.isNull()) {
			return null;
		}
		if (node.isString()) {
			return node.stringValue();
		}
		if (node.isNumber()) {
			return node.numberValue().toString();
		}
		throw invalid(field + " is neither a number nor a string");
	}

	private static OdreException invalid(String message) {
		return new OdreException(Reason.INVALID_RESPONSE, message);
	}
}
