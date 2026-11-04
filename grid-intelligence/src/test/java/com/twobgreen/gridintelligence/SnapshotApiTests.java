package com.twobgreen.gridintelligence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;

import com.twobgreen.gridintelligence.application.SnapshotReader;
import com.twobgreen.gridintelligence.application.SpeedSnapshotRepository;
import com.twobgreen.gridintelligence.domain.GridSnapshot;
import com.twobgreen.gridintelligence.domain.Production;
import com.twobgreen.gridintelligence.domain.Quality;

/**
 * Contract K2 end to end on a real TimescaleDB: rows in speed_snapshot, HTTP out. The bodies are compared with the
 * examples of docs/api and validated against the schemas of docs/api/openapi.yaml.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SnapshotApiTests {

	private static final String LATEST = "/api/v1/national/snapshots/latest";

	// Same values as the committed fixture national-tr-measured.json and the K2 example latest-national.json
	private static final Instant OBSERVED_AT = Instant.parse("2026-10-20T14:45:00Z");

	@Autowired
	MockMvc mvc;

	@Autowired
	SpeedSnapshotRepository repository;

	@Autowired
	JdbcTemplate jdbc;

	// Wraps the real reader: reset after each test, so the failures below never leak into the others
	@MockitoSpyBean
	SnapshotReader reader;

	@BeforeEach
	void emptyTable() {
		jdbc.update("DELETE FROM speed_snapshot");
	}

	@Test
	void returnsTheLatestSnapshotAsInTheContractExample() throws Exception {
		repository.upsert(nationalMeasured());

		MockHttpServletResponse response = mvc.perform(get(LATEST)).andReturn().getResponse();

		assertThat(response.getStatus()).isEqualTo(200);
		assertThat(response.getContentType()).startsWith("application/json");
		JsonNode body = OpenApiContract.parse(response.getContentAsString());
		assertThat(OpenApiContract.violations("Snapshot", body)).isEmpty();
		assertThat(withoutStoredAt(body)).isEqualTo(withoutStoredAt(OpenApiContract.example("latest-national.json")));
		assertThat(Instant.parse(body.path("storedAt").stringValue())).isEqualTo(storedAtInDatabase());
	}

	@Test
	void missingValuesComeBackAsNullNeverZero() throws Exception {
		repository.upsert(new GridSnapshot("national", Instant.parse("2026-10-20T15:00:00Z"), BigDecimal.valueOf(31),
				49500, null, 49300, new Production(37350, null, null, null, 1450, 1180, 9100, null, null), null,
				Quality.PROVISIONAL, false));

		MockHttpServletResponse response = mvc.perform(get(LATEST)).andReturn().getResponse();

		assertThat(response.getStatus()).isEqualTo(200);
		JsonNode body = OpenApiContract.parse(response.getContentAsString());
		assertThat(OpenApiContract.violations("Snapshot", body)).isEmpty();
		assertThat(withoutStoredAt(body))
				.isEqualTo(withoutStoredAt(OpenApiContract.example("latest-national-missing-values.json")));
		assertThat(body.path("pumping").isNull()).isTrue();
		assertThat(body.path("production").path("gas").isNull()).isTrue();
	}

	@Test
	void aNewerForecastOnlyRowIsNotReturned() throws Exception {
		repository.upsert(nationalMeasured());
		repository.upsert(new GridSnapshot("national", OBSERVED_AT.plusSeconds(900), null, null, 49900, 49600, null, null,
				Quality.PROVISIONAL, false));

		JsonNode body = OpenApiContract.parse(
				mvc.perform(get(LATEST)).andReturn().getResponse().getContentAsString());

		assertThat(body.path("observedAt").stringValue()).isEqualTo("2026-10-20T14:45:00Z");
	}

	@Test
	void nothingStoredIsTheDocumentedNoDataError() throws Exception {
		MockHttpServletResponse response = mvc.perform(get(LATEST)).andReturn().getResponse();

		assertThat(response.getStatus()).isEqualTo(404);
		assertProblem(response, "error-no-data.json");
	}

	@Test
	void onlyForecastRowsIsAlsoNoData() throws Exception {
		repository.upsert(new GridSnapshot("national", OBSERVED_AT, null, null, 49900, 49600, null, null,
				Quality.PROVISIONAL, false));

		MockHttpServletResponse response = mvc.perform(get(LATEST)).andReturn().getResponse();

		assertThat(response.getStatus()).isEqualTo(404);
		assertProblem(response, "error-no-data.json");
	}

	@Test
	void databaseDownIsTheDocumentedServiceUnavailable() throws Exception {
		doThrow(new CannotGetJdbcConnectionException("connection refused", new java.sql.SQLException("secret detail")))
				.when(reader).latestWithMeasures(anyString());

		MockHttpServletResponse response = mvc.perform(get(LATEST)).andReturn().getResponse();

		assertThat(response.getStatus()).isEqualTo(503);
		assertProblem(response, "error-unavailable.json");
		assertThat(response.getContentAsString()).doesNotContain("secret detail").doesNotContain("connection refused");
	}

	@Test
	void anyOtherFailureIsTheDocumentedInternalErrorWithoutLeakingDetails() throws Exception {
		doThrow(new IllegalStateException("secret internal detail")).when(reader).latestWithMeasures(anyString());

		MockHttpServletResponse response = mvc.perform(get(LATEST)).andReturn().getResponse();

		assertThat(response.getStatus()).isEqualTo(500);
		assertProblem(response, "error-internal.json");
		assertThat(response.getContentAsString()).doesNotContain("secret internal detail");
	}

	// Spring's own errors keep their real status instead of being turned into 500 by the catch-all. They are outside
	// the contract and have no `code`, so they are not checked against the Problem schema.

	@Test
	void unknownRouteIsNotFoundNotAnInternalError() throws Exception {
		MockHttpServletResponse response = mvc.perform(get("/api/v1/unknown")).andReturn().getResponse();

		assertThat(response.getStatus()).isEqualTo(404);
		assertProblemJson(response);
	}

	@Test
	void postOnTheEndpointIsMethodNotAllowed() throws Exception {
		MockHttpServletResponse response = mvc.perform(post(LATEST)).andReturn().getResponse();

		assertThat(response.getStatus()).isEqualTo(405);
		assertThat(response.getHeader("Allow")).contains("GET");
		assertProblemJson(response);
	}

	@Test
	void aClientThatRefusesJsonGetsNotAcceptableNotAnInternalError() throws Exception {
		repository.upsert(nationalMeasured());

		MockHttpServletResponse response = mvc.perform(get(LATEST).accept(MediaType.APPLICATION_XML)).andReturn()
				.getResponse();

		// The client refused JSON, so the error may come back without a body
		assertThat(response.getStatus()).isEqualTo(406);
		if (!response.getContentAsString().isEmpty()) {
			assertProblemJson(response);
		}
	}

	// The body is the example of the contract, and valid for the Problem schema of the spec
	private void assertProblem(MockHttpServletResponse response, String example) throws Exception {
		assertProblemJson(response);
		JsonNode body = OpenApiContract.parse(response.getContentAsString());
		assertThat(OpenApiContract.violations("Problem", body)).isEmpty();
		assertThat(withoutDefaultType(body)).isEqualTo(withoutDefaultType(OpenApiContract.example(example)));
	}

	// RFC 7807: an absent `type` means "about:blank" (the default of the spec), Spring omits it, the examples spell it out
	@SuppressWarnings("unchecked")
	private static Map<String, Object> withoutDefaultType(JsonNode problem) {
		Map<String, Object> values = (Map<String, Object>) OpenApiContract.withoutNulls(problem);
		values.remove("type", "about:blank");
		return values;
	}

	private static void assertProblemJson(MockHttpServletResponse response) {
		assertThat(response.getStatus()).isGreaterThanOrEqualTo(400);
		assertThat(response.getContentType()).startsWith("application/problem+json");
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> withoutStoredAt(JsonNode snapshot) {
		Map<String, Object> values = (Map<String, Object>) OpenApiContract.withoutNulls(snapshot);
		values.remove("storedAt");
		return values;
	}

	private Instant storedAtInDatabase() {
		return jdbc.queryForObject("SELECT stored_at FROM speed_snapshot", Timestamp.class).toInstant();
	}

	private static GridSnapshot nationalMeasured() {
		return new GridSnapshot("national", OBSERVED_AT, BigDecimal.valueOf(29), 49970, 49950, 49650,
				new Production(38499, 2632, 0, 37, null, 6225, 4656, 3768, 941), 0, Quality.PROVISIONAL, false);
	}
}
