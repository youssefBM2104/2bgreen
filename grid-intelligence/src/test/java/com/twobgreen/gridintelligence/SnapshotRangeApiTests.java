package com.twobgreen.gridintelligence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;

import com.twobgreen.gridintelligence.application.BatchSnapshotRepository;
import com.twobgreen.gridintelligence.application.SnapshotReader;
import com.twobgreen.gridintelligence.application.SpeedSnapshotRepository;
import com.twobgreen.gridintelligence.domain.GridSnapshot;
import com.twobgreen.gridintelligence.domain.Production;
import com.twobgreen.gridintelligence.domain.Quality;

/**
 * G8: the range endpoints and the regional scopes of contract K2, on a real TimescaleDB. Rows go into the two layer
 * tables, HTTP comes out, and every body is validated against the schemas of docs/api/openapi.yaml.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SnapshotRangeApiTests {

	private static final String NATIONAL_RANGE = "/api/v1/national/snapshots";

	private static final String REGION_RANGE = "/api/v1/regions/76/snapshots";

	@Autowired
	MockMvc mvc;

	@Autowired
	SpeedSnapshotRepository speed;

	@Autowired
	BatchSnapshotRepository batch;

	@Autowired
	JdbcTemplate jdbc;

	@MockitoSpyBean
	SnapshotReader reader;

	@BeforeEach
	void emptyTables() {
		jdbc.update("DELETE FROM speed_snapshot");
		jdbc.update("DELETE FROM batch_snapshot");
	}

	// ---- acceptance: a range mixing batch and provisional ----------------------------------------------------------

	@Test
	void aRangeMixingBatchAndProvisionalHasNoGapAndNoDuplicate() throws Exception {
		// Batch up to the end of August. The speed layer also holds the last two quarter-hours of August (batch wins)
		// and the first hour of September (nothing in batch yet).
		batch.upsert(List.of(
				national("2026-08-31T21:00:00Z", Quality.CONSOLIDATED, 45070, 29),
				national("2026-08-31T21:30:00Z", Quality.CONSOLIDATED, 43979, 29)));
		speed.upsert(national("2026-08-31T21:30:00Z", Quality.PROVISIONAL, 1, 99));
		speed.upsert(national("2026-08-31T21:45:00Z", Quality.PROVISIONAL, 2, 99));
		speed.upsert(national("2026-09-01T00:00:00Z", Quality.PROVISIONAL, 49000, 11));
		speed.upsert(national("2026-09-01T00:15:00Z", Quality.PROVISIONAL, 50000, 11));
		speed.upsert(national("2026-09-01T00:30:00Z", Quality.PROVISIONAL, 49492, 11));

		MockHttpServletResponse response = range(NATIONAL_RANGE, "2026-08-31T21:00:00Z", "2026-09-01T01:00:00Z");

		assertThat(response.getStatus()).isEqualTo(200);
		JsonNode body = OpenApiContract.parse(response.getContentAsString());
		assertThat(OpenApiContract.violations("SnapshotRange", body)).isEmpty();
		assertThat(body.path("scope").stringValue()).isEqualTo("national");
		assertThat(body.path("from").stringValue()).isEqualTo("2026-08-31T21:00:00Z");
		assertThat(body.path("to").stringValue()).isEqualTo("2026-09-01T01:00:00Z");
		assertThat(field(body, "observedAt")).containsExactly("2026-08-31T21:00:00Z", "2026-08-31T21:30:00Z",
				"2026-09-01T00:00:00Z", "2026-09-01T00:30:00Z");
		assertThat(field(body, "quality")).containsExactly("CONSOLIDATED", "CONSOLIDATED", "PROVISIONAL",
				"PROVISIONAL");
		// The batch row of 21:30 is the one served, not the provisional 1 and 2; the provisional hour is averaged
		assertThat(field(body, "consumption")).containsExactly("45070", "43979", "49500", "49492");
		Set<String> instants = new HashSet<>(field(body, "observedAt"));
		assertThat(instants).hasSameSizeAs(field(body, "observedAt"));
	}

	@Test
	void aHoleInTheDataIsStillAHole() throws Exception {
		speed.upsert(national("2026-10-20T12:00:00Z", Quality.PROVISIONAL, 49000, 11));
		speed.upsert(national("2026-10-20T13:00:00Z", Quality.PROVISIONAL, 48000, 11));

		JsonNode body = rangeBody(NATIONAL_RANGE, "2026-10-20T12:00:00Z", "2026-10-20T14:00:00Z");

		assertThat(field(body, "observedAt")).containsExactly("2026-10-20T12:00:00Z", "2026-10-20T13:00:00Z");
	}

	@Test
	void fromIsInclusiveAndToIsExclusive() throws Exception {
		batch.upsert(List.of(
				national("2026-08-31T20:00:00Z", Quality.DEFINITIVE, 1, 10),
				national("2026-08-31T20:30:00Z", Quality.DEFINITIVE, 2, 10),
				national("2026-08-31T21:00:00Z", Quality.DEFINITIVE, 3, 10)));

		JsonNode body = rangeBody(NATIONAL_RANGE, "2026-08-31T20:30:00Z", "2026-08-31T21:00:00Z");

		assertThat(field(body, "observedAt")).containsExactly("2026-08-31T20:30:00Z");
	}

	@Test
	void anEmptyRangeIsAnEmptyListNotAnError() throws Exception {
		MockHttpServletResponse response = range(NATIONAL_RANGE, "2026-10-20T00:00:00Z", "2026-10-21T00:00:00Z");

		assertThat(response.getStatus()).isEqualTo(200);
		JsonNode body = OpenApiContract.parse(response.getContentAsString());
		assertThat(OpenApiContract.violations("SnapshotRange", body)).isEmpty();
		assertThat(body.path("items").size()).isZero();
	}

	@Test
	void anAveragedFigureIsWrittenWithoutTheZerosOfTheAverage() throws Exception {
		speed.upsert(national("2026-10-20T12:00:00Z", Quality.PROVISIONAL, 49000, 11));
		speed.upsert(national("2026-10-20T12:15:00Z", Quality.PROVISIONAL, 50000, 11));

		MockHttpServletResponse response = range(NATIONAL_RANGE, "2026-10-20T12:00:00Z", "2026-10-20T13:00:00Z");

		assertThat(response.getContentAsString()).contains("\"co2Intensity\":11,");
	}

	// ---- acceptance: the regional scopes answer ----------------------------------------------------------------------

	@Test
	void aRegionAnswersItsLatestAndItsRangeAndNeverMixesWithTheNation() throws Exception {
		speed.upsert(region("76", "2026-10-20T14:30:00Z", 3800));
		speed.upsert(region("76", "2026-10-20T14:45:00Z", 3810));
		speed.upsert(national("2026-10-20T14:30:00Z", Quality.PROVISIONAL, 49000, 11));
		speed.upsert(region("32", "2026-10-20T14:30:00Z", 5000));

		MockHttpServletResponse latest = mvc.perform(get("/api/v1/regions/76/snapshots/latest")).andReturn()
				.getResponse();
		assertThat(latest.getStatus()).isEqualTo(200);
		JsonNode latestBody = OpenApiContract.parse(latest.getContentAsString());
		assertThat(OpenApiContract.violations("Snapshot", latestBody)).isEmpty();
		assertThat(latestBody.path("scope").stringValue()).isEqualTo("region:76");
		assertThat(latestBody.path("observedAt").stringValue()).isEqualTo("2026-10-20T14:45:00Z");
		assertThat(latestBody.path("consumption").intValue()).isEqualTo(3810);
		assertThat(latestBody.path("co2Intensity").isNull()).isTrue();

		JsonNode rangeBody = rangeBody(REGION_RANGE, "2026-10-20T14:00:00Z", "2026-10-20T15:00:00Z");
		assertThat(rangeBody.path("scope").stringValue()).isEqualTo("region:76");
		assertThat(field(rangeBody, "scope")).containsExactly("region:76");
		assertThat(field(rangeBody, "consumption")).containsExactly("3805");
		assertThat(field(rangeBody, "quality")).containsExactly("PROVISIONAL");
	}

	@Test
	void aWellFormedRegionWithoutDataIsNoDataForLatestAndAnEmptyRange() throws Exception {
		MockHttpServletResponse latest = mvc.perform(get("/api/v1/regions/76/snapshots/latest")).andReturn()
				.getResponse();
		assertThat(latest.getStatus()).isEqualTo(404);
		assertThat(OpenApiContract.parse(latest.getContentAsString()).path("code").stringValue()).isEqualTo("NO_DATA");

		MockHttpServletResponse range = range(REGION_RANGE, "2026-10-20T00:00:00Z", "2026-10-21T00:00:00Z");
		assertThat(range.getStatus()).isEqualTo(200);
		assertThat(OpenApiContract.parse(range.getContentAsString()).path("items").size()).isZero();
	}

	@Test
	void aRegionCodeThatIsNotAnInseeCodeIsTheDocumentedUnknownScope() throws Exception {
		MockHttpServletResponse response = mvc.perform(get("/api/v1/regions/xx/snapshots/latest")).andReturn()
				.getResponse();

		assertThat(response.getStatus()).isEqualTo(404);
		assertProblem(response, "error-scope-unknown.json");
	}

	@ParameterizedTest
	@ValueSource(strings = { "xx", "7", "123", "7a", "ab" })
	void everyMalformedRegionCodeIsUnknownOnBothRegionalPaths(String code) throws Exception {
		MockHttpServletResponse latest = mvc.perform(get("/api/v1/regions/" + code + "/snapshots/latest"))
				.andReturn().getResponse();
		MockHttpServletResponse range = range("/api/v1/regions/" + code + "/snapshots", "2026-10-20T00:00:00Z",
				"2026-10-21T00:00:00Z");

		for (MockHttpServletResponse response : List.of(latest, range)) {
			assertThat(response.getStatus()).isEqualTo(404);
			JsonNode body = OpenApiContract.parse(response.getContentAsString());
			assertThat(OpenApiContract.violations("Problem", body)).isEmpty();
			assertThat(body.path("code").stringValue()).isEqualTo("SCOPE_UNKNOWN");
		}
	}

	// ---- INVALID_PARAMETER -------------------------------------------------------------------------------------------

	@Test
	void fromNotBeforeToIsTheDocumentedInvalidParameter() throws Exception {
		MockHttpServletResponse response = range(NATIONAL_RANGE, "2026-10-21T00:00:00Z", "2026-10-21T00:00:00Z");

		assertThat(response.getStatus()).isEqualTo(400);
		assertProblem(response, "error-invalid-parameter.json");
	}

	@Test
	void missingOrMalformedDatesAreInvalidParameterWithACode() throws Exception {
		List<MockHttpServletResponse> responses = new ArrayList<>();
		responses.add(mvc.perform(get(NATIONAL_RANGE)).andReturn().getResponse());
		responses.add(mvc.perform(get(NATIONAL_RANGE).param("from", "2026-10-20T00:00:00Z")).andReturn().getResponse());
		responses.add(range(NATIONAL_RANGE, "yesterday", "2026-10-21T00:00:00Z"));
		responses.add(range(NATIONAL_RANGE, "2026-10-20", "2026-10-21T00:00:00Z"));
		responses.add(range(NATIONAL_RANGE, "2026-10-20T00:00:00Z", ""));

		for (MockHttpServletResponse response : responses) {
			assertThat(response.getStatus()).isEqualTo(400);
			JsonNode body = OpenApiContract.parse(response.getContentAsString());
			assertThat(OpenApiContract.violations("Problem", body)).isEmpty();
			assertThat(body.path("code").stringValue()).isEqualTo("INVALID_PARAMETER");
			// The input is never reflected
			assertThat(response.getContentAsString()).doesNotContain("yesterday");
		}
	}

	@Test
	void aRangeWiderThanTheLimitIsRefusedAndNotCut() throws Exception {
		MockHttpServletResponse response = range(NATIONAL_RANGE, "2025-01-01T00:00:00Z", "2026-01-02T00:00:01Z");

		assertThat(response.getStatus()).isEqualTo(400);
		assertProblem(response, "error-range-too-large.json");
	}

	@Test
	void aRangeOfExactlyTheLimitIsServed() throws Exception {
		MockHttpServletResponse response = range(NATIONAL_RANGE, "2025-01-01T00:00:00Z", "2026-01-02T00:00:00Z");

		assertThat(response.getStatus()).isEqualTo(200);
	}

	@Test
	void theRegionIsCheckedBeforeThePeriod() throws Exception {
		MockHttpServletResponse response = range("/api/v1/regions/xx/snapshots", "garbage", "garbage");

		assertThat(response.getStatus()).isEqualTo(404);
	}

	// ---- failures ----------------------------------------------------------------------------------------------------

	@Test
	void databaseDownOnARangeIsTheDocumentedServiceUnavailable() throws Exception {
		doThrow(new CannotGetJdbcConnectionException("connection refused", new java.sql.SQLException("secret detail")))
				.when(reader).range(any(), any(), any());

		MockHttpServletResponse response = range(NATIONAL_RANGE, "2026-10-20T00:00:00Z", "2026-10-21T00:00:00Z");

		assertThat(response.getStatus()).isEqualTo(503);
		assertThat(OpenApiContract.parse(response.getContentAsString()).path("code").stringValue())
				.isEqualTo("SERVICE_UNAVAILABLE");
		assertThat(response.getContentAsString()).doesNotContain("secret detail");
	}

	// ---- helpers -----------------------------------------------------------------------------------------------------

	private MockHttpServletResponse range(String path, String from, String to) throws Exception {
		return mvc.perform(get(path).param("from", from).param("to", to)).andReturn().getResponse();
	}

	private JsonNode rangeBody(String path, String from, String to) throws Exception {
		MockHttpServletResponse response = range(path, from, to);
		assertThat(response.getStatus()).isEqualTo(200);
		JsonNode body = OpenApiContract.parse(response.getContentAsString());
		assertThat(OpenApiContract.violations("SnapshotRange", body)).isEmpty();
		return body;
	}

	/** One field of every item of a range, as text, in the order of the answer. */
	private static List<String> field(JsonNode body, String name) {
		List<String> values = new ArrayList<>();
		JsonNode items = body.path("items");
		for (int i = 0; i < items.size(); i++) {
			JsonNode value = items.get(i).path(name);
			values.add(value.isString() ? value.stringValue() : value.asString());
		}
		return values;
	}

	private void assertProblem(MockHttpServletResponse response, String example) throws Exception {
		assertThat(response.getContentType()).startsWith("application/problem+json");
		JsonNode body = OpenApiContract.parse(response.getContentAsString());
		assertThat(OpenApiContract.violations("Problem", body)).isEmpty();
		assertThat(withoutDefaultType(body)).isEqualTo(withoutDefaultType(OpenApiContract.example(example)));
	}

	// RFC 7807: an absent `type` means "about:blank", Spring omits it, the examples spell it out
	@SuppressWarnings("unchecked")
	private static Map<String, Object> withoutDefaultType(JsonNode problem) {
		Map<String, Object> values = (Map<String, Object>) OpenApiContract.withoutNulls(problem);
		values.remove("type", "about:blank");
		return values;
	}

	private static GridSnapshot national(String observedAt, Quality quality, int consumption, int co2) {
		return new GridSnapshot("national", Instant.parse(observedAt), BigDecimal.valueOf(co2), consumption, null, null,
				Production.NONE, null, quality, false);
	}

	/** A regional reading: no CO2 and no forecast, like the data of ODRE. */
	private static GridSnapshot region(String code, String observedAt, int consumption) {
		return new GridSnapshot(GridSnapshot.regionScope(code), Instant.parse(observedAt), null, consumption, null,
				null, Production.NONE, null, Quality.PROVISIONAL, false);
	}
}
