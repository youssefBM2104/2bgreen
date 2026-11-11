package com.twobgreen.platform.automation.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.twobgreen.platform.TestcontainersConfiguration;
import com.twobgreen.platform.advisory.GridScope;
import com.twobgreen.platform.advisory.GridUnavailableException;
import com.twobgreen.platform.advisory.application.GridReadings;
import com.twobgreen.platform.advisory.domain.GridQuality;
import com.twobgreen.platform.advisory.domain.GridReading;
import com.twobgreen.platform.automation.application.ManageApiKeys;
import com.twobgreen.platform.automation.application.NewApiKey;

/**
 * The public API of U7 through the real API-key chain, with the grid port faked. Each body, success or error, is
 * checked against the schema of {@code docs/api/public-openapi.yaml}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CarbonIntensityApiTests {

	private static final String LATEST = "/api/v1/public/intensity/latest";

	private static final String RANGE = "/api/v1/public/intensity";

	private final String owner = UUID.randomUUID().toString();

	@Autowired
	MockMvc mvc;

	@Autowired
	ManageApiKeys manageApiKeys;

	@Autowired
	JwtAuthenticationConverter converter;

	@MockitoBean
	GridReadings readings;

	@Test
	void latestWithAValidKeyMatchesTheContract() throws Exception {
		Instant observedAt = Instant.parse("2026-10-20T14:45:00Z");
		when(readings.latest(GridScope.NATIONAL)).thenReturn(Optional.of(new GridReading("national", observedAt,
				new BigDecimal("29"), GridQuality.PROVISIONAL, false, Instant.parse("2026-10-20T15:03:12Z"))));

		String body = withKey(get(LATEST))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(header().string("X-RateLimit-Limit", "1000"))
				.andExpect(header().string("X-RateLimit-Remaining", "999"))
				.andExpect(header().exists("X-RateLimit-Reset"))
				.andExpect(jsonPath("$.scope").value("national"))
				.andExpect(jsonPath("$.co2Intensity").value(29))
				.andExpect(jsonPath("$.quality").value("PROVISIONAL"))
				// The reading is weeks old by now: no colour, whatever its value
				.andExpect(jsonPath("$.trafficLight").value("UNKNOWN"))
				.andExpect(jsonPath("$.stale").value(true))
				.andReturn().getResponse().getContentAsString();

		assertThat(PublicApiContract.violations("LatestIntensity", body)).isEmpty();
	}

	@Test
	void latestOfARecentReadingHasTheColourOfTheCitizenLight() throws Exception {
		Instant observedAt = Instant.now().minus(Duration.ofMinutes(30));
		when(readings.latest(GridScope.NATIONAL)).thenReturn(Optional.of(new GridReading("national", observedAt,
				new BigDecimal("12.5"), GridQuality.PROVISIONAL, false, observedAt)));

		withKey(get(LATEST))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.trafficLight").value("GREEN"))
				.andExpect(jsonPath("$.stale").value(false));
	}

	@Test
	void regionWithoutCo2HasANullValueAndNoColour() throws Exception {
		GridScope region = new GridScope("region:76");
		Instant observedAt = Instant.now();
		when(readings.latest(region)).thenReturn(Optional.of(
				new GridReading("region:76", observedAt, null, GridQuality.PROVISIONAL, false, observedAt)));

		String body = withKey(get(LATEST).param("scope", "region:76"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.scope").value("region:76"))
				.andExpect(jsonPath("$.co2Intensity").isEmpty())
				.andExpect(jsonPath("$.trafficLight").value("UNKNOWN"))
				.andReturn().getResponse().getContentAsString();

		assertThat(body).contains("\"co2Intensity\":null");
		assertThat(PublicApiContract.violations("LatestIntensity", body)).isEmpty();
	}

	@Test
	void nothingStoredYetIsNoData() throws Exception {
		when(readings.latest(GridScope.NATIONAL)).thenReturn(Optional.empty());

		expectProblem(withKey(get(LATEST)), 404, "NO_DATA");
	}

	@Test
	void gridDownIsUnavailableWithoutInternals() throws Exception {
		when(readings.latest(any())).thenThrow(new GridUnavailableException("grid-intelligence answered HTTP 500"));
		when(readings.between(any(), any(), any()))
				.thenThrow(new GridUnavailableException("grid-intelligence answered HTTP 500"));

		String body = expectProblem(withKey(get(LATEST)), 503, "GRID_UNAVAILABLE");
		expectProblem(withKey(get(RANGE).param("from", "2026-11-09T00:00:00Z").param("to", "2026-11-10T00:00:00Z")),
				503, "GRID_UNAVAILABLE");

		assertThat(body).doesNotContain("grid-intelligence", "HTTP 500");
	}

	@Test
	void rangeOfSevenDaysMatchesTheContract() throws Exception {
		Instant from = Instant.parse("2026-11-03T00:00:00Z");
		Instant to = from.plus(Duration.ofDays(7));
		when(readings.between(GridScope.NATIONAL, from, to)).thenReturn(List.of(
				new GridReading("national", from, new BigDecimal("27"), GridQuality.CONSOLIDATED, false, from),
				new GridReading("national", from.plusSeconds(1800), null, GridQuality.PROVISIONAL, false, from)));

		String body = withKey(get(RANGE).param("from", from.toString()).param("to", to.toString()))
				.andExpect(status().isOk())
				.andExpect(header().string("X-RateLimit-Limit", "1000"))
				.andExpect(jsonPath("$.scope").value("national"))
				.andExpect(jsonPath("$.from").value("2026-11-03T00:00:00Z"))
				.andExpect(jsonPath("$.to").value("2026-11-10T00:00:00Z"))
				.andExpect(jsonPath("$.items.length()").value(2))
				.andExpect(jsonPath("$.items[0].co2Intensity").value(27))
				.andExpect(jsonPath("$.items[0].quality").value("CONSOLIDATED"))
				.andExpect(jsonPath("$.items[1].co2Intensity").isEmpty())
				.andReturn().getResponse().getContentAsString();

		assertThat(PublicApiContract.violations("IntensityRange", body)).isEmpty();
	}

	@Test
	void rangeOfARegionAsksForThatRegion() throws Exception {
		GridScope region = new GridScope("region:84");
		when(readings.between(any(), any(), any())).thenReturn(List.of());

		withKey(get(RANGE).param("scope", "region:84").param("from", "2026-11-09T00:00:00Z")
				.param("to", "2026-11-10T00:00:00Z"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.scope").value("region:84"))
				.andExpect(jsonPath("$.items").isEmpty());

		verify(readings).between(region, Instant.parse("2026-11-09T00:00:00Z"), Instant.parse("2026-11-10T00:00:00Z"));
	}

	@Test
	void rangeWiderThanSevenDaysIsRefusedNotCut() throws Exception {
		String body = expectProblem(withKey(get(RANGE).param("from", "2026-11-03T00:00:00Z")
				.param("to", "2026-11-10T00:00:01Z")), 400, "INVALID_PARAMETER");

		assertThat(body).contains("7 days");
		verify(readings, never()).between(any(), any(), any());
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"from=&to=2026-11-10T00:00:00Z",
			"to=2026-11-10T00:00:00Z",
			"from=2026-11-10T00:00:00Z",
			"from=yesterday&to=2026-11-10T00:00:00Z",
			"from=2026-11-10&to=2026-11-11",
			"from=2026-11-10T00:00:00Z&to=2026-11-10T00:00:00Z",
			"from=2026-11-10T00:00:00Z&to=2026-11-09T00:00:00Z",
			"scope=France&from=2026-11-09T00:00:00Z&to=2026-11-10T00:00:00Z",
			"scope=region:7&from=2026-11-09T00:00:00Z&to=2026-11-10T00:00:00Z" })
	void wrongRangeParametersAreInvalid(String query) throws Exception {
		expectProblem(withKey(get(RANGE + "?" + query)), 400, "INVALID_PARAMETER");

		verifyNoInteractions(readings);
	}

	@ParameterizedTest
	@ValueSource(strings = { "France", "region:", "region:7", "region:076", "REGION:76" })
	void malformedScopeIsInvalid(String scope) throws Exception {
		expectProblem(withKey(get(LATEST).param("scope", scope)), 400, "INVALID_PARAMETER");

		verifyNoInteractions(readings);
	}

	@Test
	void missingKeyIsUnauthorized() throws Exception {
		expectProblem(mvc.perform(get(LATEST)), 401, "API_KEY_REQUIRED");
		expectProblem(mvc.perform(get(RANGE)), 401, "API_KEY_REQUIRED");

		verifyNoInteractions(readings);
	}

	@Test
	void revokedKeyIsUnauthorized() throws Exception {
		NewApiKey key = manageApiKeys.create(owner, "ci");
		manageApiKeys.revoke(owner, key.usage().key().id());

		expectProblem(mvc.perform(get(LATEST).header("X-API-Key", key.secret())), 401, "INVALID_API_KEY");

		verifyNoInteractions(readings);
	}

	@Test
	void keycloakTokenAloneIsUnauthorized() throws Exception {
		for (String role : List.of("devops", "citizen", "regional_official")) {
			expectProblem(mvc.perform(get(LATEST).with(jwt()
					.jwt(j -> j.subject(owner).claim("realm_access", Map.of("roles", List.of(role))))
					.authorities(token -> converter.convert(token).getAuthorities()))), 401, "API_KEY_REQUIRED");
		}

		verifyNoInteractions(readings);
	}

	private ResultActions withKey(MockHttpServletRequestBuilder request) throws Exception {
		return mvc.perform(request.header("X-API-Key", manageApiKeys.create(owner, "ci").secret()));
	}

	/** Checks status, media type, code and schema, and returns the body. */
	private static String expectProblem(ResultActions result, int status, String code) throws Exception {
		String body = result
				.andExpect(status().is(status))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value(code))
				.andReturn().getResponse().getContentAsString();
		assertThat(PublicApiContract.violations("Problem", body)).isEmpty();
		return body;
	}
}
