package com.twobgreen.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.twobgreen.platform.advisory.application.GridReadings;
import com.twobgreen.platform.advisory.GridUnavailableException;
import com.twobgreen.platform.advisory.domain.GridQuality;
import com.twobgreen.platform.advisory.domain.GridReading;

/** What the frontend sees, through the real security chain, whatever happens behind the port. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class TrafficLightControllerTests {

	private static final String LATEST = "/api/v1/advisory/traffic-light";

	private static GridReading reading(BigDecimal co2, Instant observedAt) {
		return new GridReading("national", observedAt, co2, GridQuality.PROVISIONAL, false, observedAt);
	}

	@Autowired
	MockMvc mvc;

	@Autowired
	JwtAuthenticationConverter converter;

	@MockitoBean
	GridReadings readings;

	private JwtRequestPostProcessor tokenWithRole(String role) {
		return jwt().jwt(j -> j.claim("realm_access", Map.of("roles", List.of(role))))
				.authorities(token -> converter.convert(token).getAuthorities());
	}

	@Test
	void withoutTokenIsUnauthorized() throws Exception {
		mvc.perform(get(LATEST)).andExpect(status().isUnauthorized());
	}

	@Test
	void otherRolesAreForbidden() throws Exception {
		mvc.perform(get(LATEST).with(tokenWithRole("devops"))).andExpect(status().isForbidden());
		mvc.perform(get(LATEST).with(tokenWithRole("regional_official"))).andExpect(status().isForbidden());
	}

	@ParameterizedTest
	@CsvSource({ "19, GREEN", "20, AMBER", "39, AMBER", "40, RED" })
	void citizenGetsTheLevelOfTheConfiguredLimits(BigDecimal co2, String level) throws Exception {
		Instant observedAt = Instant.now().minusSeconds(60);
		when(readings.latestNational()).thenReturn(Optional.of(reading(co2, observedAt)));

		mvc.perform(get(LATEST).with(tokenWithRole("citizen")))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.level").value(level))
				.andExpect(jsonPath("$.co2Intensity").value(co2.intValue()))
				.andExpect(jsonPath("$.observedAt").value(observedAt.toString()))
				.andExpect(jsonPath("$.stale").value(false));
	}

	@Test
	void limitsBehindTheColoursAreThoseOfTheConfiguration() throws Exception {
		mvc.perform(get(LATEST + "/limits").with(tokenWithRole("citizen")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.greenBelow").value(20))
				.andExpect(jsonPath("$.amberBelow").value(40));
		mvc.perform(get(LATEST + "/limits").with(tokenWithRole("devops"))).andExpect(status().isForbidden());
	}

	@Test
	void anOldReadingIsNotPresentedAsCurrent() throws Exception {
		Instant observedAt = Instant.now().minus(Duration.ofHours(3));
		when(readings.latestNational()).thenReturn(Optional.of(reading(new BigDecimal("10"), observedAt)));

		mvc.perform(get(LATEST).with(tokenWithRole("citizen")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.level").value("UNKNOWN"))
				.andExpect(jsonPath("$.stale").value(true))
				.andExpect(jsonPath("$.observedAt").value(observedAt.toString()));
	}

	@Test
	void missingCo2IsUnknownAndStaysNull() throws Exception {
		when(readings.latestNational()).thenReturn(Optional.of(reading(null, Instant.now())));

		mvc.perform(get(LATEST).with(tokenWithRole("citizen")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.level").value("UNKNOWN"))
				.andExpect(jsonPath("$.stale").value(false))
				.andExpect(jsonPath("$.co2Intensity").value((Object) null));
	}

	@Test
	void noDataIsAProblemWithAStableCode() throws Exception {
		when(readings.latestNational()).thenReturn(Optional.empty());

		mvc.perform(get(LATEST).with(tokenWithRole("citizen")))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("NO_DATA"));
	}

	@Test
	void unavailableIsAProblemThatLeaksNothing() throws Exception {
		when(readings.latestNational()).thenThrow(new GridUnavailableException(
				"internal detail http://localhost:8081", new ConnectException("secret cause")));

		String body = mvc.perform(get(LATEST).with(tokenWithRole("citizen")))
				.andExpect(status().isServiceUnavailable())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("GRID_UNAVAILABLE"))
				.andReturn().getResponse().getContentAsString();

		assertThat(body).doesNotContain("8081", "internal detail", "secret cause", "Exception");
	}
}
