package com.twobgreen.platform.regional.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
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

import com.twobgreen.platform.TestcontainersConfiguration;
import com.twobgreen.platform.advisory.GridScope;
import com.twobgreen.platform.advisory.GridUnavailableException;
import com.twobgreen.platform.advisory.application.GridReadings;
import com.twobgreen.platform.advisory.domain.GridMix;
import com.twobgreen.platform.advisory.domain.GridQuality;
import com.twobgreen.platform.advisory.domain.GridReading;

/**
 * The regional dashboard (U11) as the frontend sees it, through the real security chain: only the port to
 * grid-intelligence is replaced.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class RegionalDashboardControllerTests {

	private static final String DASHBOARD = "/api/v1/regional/dashboard";

	private static final GridScope PACA = new GridScope("region:93");

	/** Provence-Alpes-Côte d'Azur, eco2mix-regional-tr 2026-11-10 12:00 UTC, CO2 estimated as G7 does. */
	private static final GridMix PACA_MIX = new GridMix(4559, 0, null, null, null, 1417, 25, 556, 1606, 138);

	/** The nation at the same half hour (illustrative values, the split of national data). */
	private static final GridMix FRANCE_MIX = new GridMix(59437, 41561, 6767, 547, 37, null, 3355, 7919, 6240, 959);

	@Autowired
	MockMvc mvc;

	@Autowired
	JwtAuthenticationConverter converter;

	@MockitoBean
	GridReadings readings;

	private JwtRequestPostProcessor token(String role, String regionCode) {
		Map<String, Object> claims = new HashMap<>();
		claims.put("realm_access", Map.of("roles", List.of(role)));
		if (regionCode != null) {
			claims.put("region_code", regionCode);
		}
		return jwt().jwt(j -> j.claims(c -> c.putAll(claims)))
				.authorities(t -> converter.convert(t).getAuthorities());
	}

	private JwtRequestPostProcessor official(String regionCode) {
		return token("regional_official", regionCode);
	}

	private static GridReading reading(String scope, Instant observedAt, BigDecimal co2, boolean estimated,
			GridMix mix) {
		return new GridReading(scope, observedAt, co2, GridQuality.PROVISIONAL, estimated, observedAt.plusSeconds(4500),
				mix);
	}

	@Test
	void withoutTokenIsUnauthorized() throws Exception {
		mvc.perform(get(DASHBOARD)).andExpect(status().isUnauthorized());
	}

	@Test
	void otherRolesAreForbiddenEvenWithARegion() throws Exception {
		mvc.perform(get(DASHBOARD).with(token("citizen", "93"))).andExpect(status().isForbidden());
		mvc.perform(get(DASHBOARD).with(token("devops", "93"))).andExpect(status().isForbidden());
	}

	@Test
	void anOfficialWithoutRegionIsForbiddenWithAStableCode() throws Exception {
		mvc.perform(get(DASHBOARD).with(official(null)))
				.andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("REGION_NOT_ASSIGNED"));
		mvc.perform(get(DASHBOARD).with(official("94")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("REGION_NOT_ASSIGNED"));
		verify(readings, never()).latest(any());
	}

	@Test
	void aRegionWithoutDataIsNoData() throws Exception {
		when(readings.latest(PACA)).thenReturn(Optional.empty());

		mvc.perform(get(DASHBOARD).with(official("93")))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("NO_DATA"));
	}

	@Test
	void showsTheLatestRegionalReadingAndTheNationAtTheSameHalfHour() throws Exception {
		Instant observedAt = Instant.now().minus(Duration.ofHours(2)).truncatedTo(ChronoUnit.HOURS)
				.plus(Duration.ofMinutes(45));
		Instant halfHour = observedAt.minus(Duration.ofMinutes(15));
		when(readings.latest(PACA))
				.thenReturn(Optional.of(reading("region:93", observedAt, new BigDecimal("181"), true, PACA_MIX)));
		when(readings.between(GridScope.NATIONAL, halfHour, halfHour.plus(Duration.ofMinutes(30))))
				.thenReturn(List.of(reading("national", halfHour, new BigDecimal("59"), false, FRANCE_MIX)));

		mvc.perform(get(DASHBOARD).with(official("93")))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.region.code").value("93"))
				.andExpect(jsonPath("$.region.name").value("Provence-Alpes-Côte d'Azur"))
				.andExpect(jsonPath("$.maxAge").value("PT4H"))
				.andExpect(jsonPath("$.regional.observedAt").value(observedAt.toString()))
				.andExpect(jsonPath("$.regional.stale").value(false))
				.andExpect(jsonPath("$.regional.quality").value("PROVISIONAL"))
				.andExpect(jsonPath("$.regional.consumption").value(4559))
				.andExpect(jsonPath("$.regional.production.thermal").value(1417))
				.andExpect(jsonPath("$.regional.production.hydro").value(1606))
				.andExpect(jsonPath("$.regional.totalProduction").value(3742))
				.andExpect(jsonPath("$.regional.coverageRate").value(82.1))
				.andExpect(jsonPath("$.regional.balance").value("IMPORTING"))
				.andExpect(jsonPath("$.regional.renewableShare").value(62.1))
				.andExpect(jsonPath("$.regional.co2Intensity").value(181))
				.andExpect(jsonPath("$.regional.co2Estimated").value(true))
				.andExpect(jsonPath("$.national.observedAt").value(halfHour.toString()))
				.andExpect(jsonPath("$.national.stale").value((Object) null))
				.andExpect(jsonPath("$.national.production.thermal").value(7351))
				.andExpect(jsonPath("$.national.totalProduction").value(67385))
				.andExpect(jsonPath("$.national.balance").value("EXPORTING"))
				.andExpect(jsonPath("$.national.co2Intensity").value(59))
				.andExpect(jsonPath("$.national.co2Estimated").value(false));
	}

	@Test
	void aRegionCannotBeAskedForInTheRequest() throws Exception {
		Instant observedAt = Instant.now().minus(Duration.ofHours(1));
		when(readings.latest(PACA))
				.thenReturn(Optional.of(reading("region:93", observedAt, new BigDecimal("181"), true, PACA_MIX)));

		mvc.perform(get(DASHBOARD).param("region", "11").param("region_code", "11").with(official("93")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.region.code").value("93"));
		verify(readings, never()).latest(new GridScope("region:11"));
	}

	@Test
	void missingValuesStayNullAndTheRatesBuiltOnThemToo() throws Exception {
		Instant observedAt = Instant.now().minus(Duration.ofHours(1));
		GridMix hydroMissing = new GridMix(4559, 0, null, null, null, 1417, 25, 556, null, 138);
		when(readings.latest(PACA)).thenReturn(Optional.of(reading("region:93", observedAt, null, false, hydroMissing)));
		when(readings.between(eq(GridScope.NATIONAL), any(), any())).thenReturn(List.of());

		mvc.perform(get(DASHBOARD).with(official("93")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.regional.consumption").value(4559))
				.andExpect(jsonPath("$.regional.production.thermal").value(1417))
				.andExpect(jsonPath("$.regional.production.hydro").value((Object) null))
				.andExpect(jsonPath("$.regional.totalProduction").value((Object) null))
				.andExpect(jsonPath("$.regional.coverageRate").value((Object) null))
				.andExpect(jsonPath("$.regional.balance").value((Object) null))
				.andExpect(jsonPath("$.regional.renewableShare").value((Object) null))
				.andExpect(jsonPath("$.regional.co2Intensity").value((Object) null))
				.andExpect(jsonPath("$.regional.co2Estimated").value(false))
				.andExpect(jsonPath("$.national").value((Object) null));
	}

	@Test
	void anOldRegionalReadingIsShownAsStale() throws Exception {
		Instant observedAt = Instant.now().minus(Duration.ofHours(5));
		when(readings.latest(PACA))
				.thenReturn(Optional.of(reading("region:93", observedAt, new BigDecimal("181"), true, PACA_MIX)));

		mvc.perform(get(DASHBOARD).with(official("93")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.regional.stale").value(true))
				.andExpect(jsonPath("$.regional.co2Intensity").value(181));
	}

	@Test
	void unavailableIsAProblemThatLeaksNothing() throws Exception {
		when(readings.latest(PACA)).thenThrow(new GridUnavailableException("internal detail http://localhost:8081",
				new ConnectException("secret cause")));

		String body = mvc.perform(get(DASHBOARD).with(official("93")))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("GRID_UNAVAILABLE"))
				.andReturn().getResponse().getContentAsString();

		assertThat(body).doesNotContain("8081", "internal detail", "secret cause", "Exception");
	}
}
