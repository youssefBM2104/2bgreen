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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
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
import com.twobgreen.platform.advisory.application.GridUnavailableException;
import com.twobgreen.platform.advisory.domain.GridQuality;
import com.twobgreen.platform.advisory.domain.GridReading;

/** What the frontend sees, through the real security chain, whatever happens behind the port. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class GridReadingControllerTests {

	private static final String LATEST = "/api/v1/advisory/grid/latest";

	private static final GridReading READING = new GridReading("national", Instant.parse("2026-10-20T14:45:00Z"),
			new BigDecimal("29"), GridQuality.PROVISIONAL, false, Instant.parse("2026-10-20T15:03:12Z"));

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

	@Test
	void citizenGetsTheReading() throws Exception {
		when(readings.latestNational()).thenReturn(Optional.of(READING));

		mvc.perform(get(LATEST).with(tokenWithRole("citizen")))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.scope").value("national"))
				.andExpect(jsonPath("$.observedAt").value("2026-10-20T14:45:00Z"))
				.andExpect(jsonPath("$.co2Intensity").value(29))
				.andExpect(jsonPath("$.quality").value("PROVISIONAL"))
				.andExpect(jsonPath("$.estimated").value(false))
				.andExpect(jsonPath("$.storedAt").value("2026-10-20T15:03:12Z"));
	}

	@Test
	void missingCo2StaysNull() throws Exception {
		when(readings.latestNational()).thenReturn(Optional.of(new GridReading("national", READING.observedAt(), null,
				GridQuality.PROVISIONAL, false, READING.storedAt())));

		mvc.perform(get(LATEST).with(tokenWithRole("citizen")))
				.andExpect(status().isOk())
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
