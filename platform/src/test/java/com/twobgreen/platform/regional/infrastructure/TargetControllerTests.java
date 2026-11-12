package com.twobgreen.platform.regional.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.twobgreen.platform.TestcontainersConfiguration;
import com.twobgreen.platform.advisory.GridScope;
import com.twobgreen.platform.advisory.application.GridReadings;
import com.twobgreen.platform.advisory.domain.GridMix;
import com.twobgreen.platform.advisory.domain.GridQuality;
import com.twobgreen.platform.advisory.domain.GridReading;
import com.twobgreen.platform.regional.TargetDeleted;
import com.twobgreen.platform.regional.application.RecalculateTargets;

/**
 * The targets of the regional official (U13) through the real security chain and the real database; only the port to
 * grid-intelligence is replaced, by readings built by hand: 40 % renewable in the reference year 2025, 50 % over the
 * last 12 months.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class TargetControllerTests {

	private static final String TARGETS = "/api/v1/regional/targets";

	private static final GridScope PACA = new GridScope("region:93");

	private static final Instant END_OF_2025 = Instant.parse("2026-01-01T00:00:00Z");

	/** 40 % renewable, production = consumption (coverage 100 %). */
	private static final GridMix REFERENCE = new GridMix(1000, 600, null, null, null, 0, 400, 0, 0, 0);

	/** 50 % renewable, coverage 125 %. */
	private static final GridMix RECENT = new GridMix(800, 500, null, null, null, 0, 500, 0, 0, 0);

	private final String user = UUID.randomUUID().toString();

	@Autowired
	MockMvc mvc;

	@Autowired
	JwtAuthenticationConverter converter;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	RecalculateTargets recalculate;

	@Autowired
	ApplicationEvents events;

	@MockitoBean
	GridReadings readings;

	@BeforeEach
	void gridData() {
		when(readings.between(eq(PACA), any(), any())).thenAnswer(call -> {
			Instant from = call.getArgument(1);
			Instant to = call.getArgument(2);
			return everyHalfHour(from, to, to.equals(END_OF_2025) ? REFERENCE : RECENT);
		});
	}

	private static List<GridReading> everyHalfHour(Instant from, Instant to, GridMix mix) {
		List<GridReading> rows = new ArrayList<>();
		for (Instant at = from; at.isBefore(to); at = at.plus(Duration.ofMinutes(30))) {
			rows.add(new GridReading("region:93", at, null, GridQuality.DEFINITIVE, false, at, mix));
		}
		return rows;
	}

	private JwtRequestPostProcessor official(String subject, String regionCode) {
		Map<String, Object> claims = new HashMap<>();
		claims.put("realm_access", Map.of("roles", List.of("regional_official")));
		if (regionCode != null) {
			claims.put("region_code", regionCode);
		}
		return jwt().jwt(j -> j.subject(subject).claims(c -> c.putAll(claims)))
				.authorities(t -> converter.convert(t).getAuthorities());
	}

	private JwtRequestPostProcessor me() {
		return official(user, "93");
	}

	private static String body(String indicator, Object referenceYear, String target, String deadline,
			String tolerance) {
		return """
				{"indicator": %s, "referenceYear": %s, "targetValue": %s, "deadline": %s, "tolerance": %s}
				""".formatted(quoted(indicator), referenceYear, target, quoted(deadline), tolerance);
	}

	private static String quoted(String value) {
		return value == null ? "null" : "\"" + value + "\"";
	}

	private long create(String target) throws Exception {
		String json = mvc.perform(post(TARGETS).with(me()).contentType(MediaType.APPLICATION_JSON)
				.content(body("RENEWABLE_SHARE", 2025, target, "2030-12-31", "2")))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(json, "$.id")).longValue();
	}

	@Test
	void withoutTokenOrWithAnotherRoleIsRefused() throws Exception {
		mvc.perform(get(TARGETS)).andExpect(status().isUnauthorized());
		mvc.perform(get(TARGETS).with(jwt().jwt(j -> j.claim("realm_access", Map.of("roles", List.of("citizen")))
				.claim("region_code", "93")).authorities(t -> converter.convert(t).getAuthorities())))
				.andExpect(status().isForbidden());
	}

	@Test
	void anOfficialWithoutRegionCanDoNothing() throws Exception {
		mvc.perform(get(TARGETS).with(official(user, null)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("REGION_NOT_ASSIGNED"));
		mvc.perform(post(TARGETS).with(official(user, null)).contentType(MediaType.APPLICATION_JSON)
				.content(body("RENEWABLE_SHARE", 2025, "60", "2030-12-31", "2")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("REGION_NOT_ASSIGNED"));
	}

	@Test
	void aCreatedTargetHasItsReferenceAndItsFirstValueAtOnce() throws Exception {
		mvc.perform(post(TARGETS).with(me()).contentType(MediaType.APPLICATION_JSON)
				.content(body("RENEWABLE_SHARE", 2025, "60", "2030-12-31", "2")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.indicator").value("RENEWABLE_SHARE"))
				.andExpect(jsonPath("$.unit").value("%"))
				.andExpect(jsonPath("$.referenceYear").value(2025))
				.andExpect(jsonPath("$.referenceValue").value(40.0))
				.andExpect(jsonPath("$.targetValue").value(60))
				.andExpect(jsonPath("$.deadline").value("2030-12-31"))
				.andExpect(jsonPath("$.current.value").value(50.0))
				.andExpect(jsonPath("$.current.percent").value(50.0))
				.andExpect(jsonPath("$.current.achieved").value(false))
				.andExpect(jsonPath("$.current.status").value("AHEAD"))
				.andExpect(jsonPath("$.history.length()").value(1));
	}

	@Test
	void aTargetAlreadyReachedIsAchievedAtOneHundredPercentNoMore() throws Exception {
		mvc.perform(post(TARGETS).with(me()).contentType(MediaType.APPLICATION_JSON)
				.content(body("RENEWABLE_SHARE", 2025, "45", "2030-12-31", null)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.current.value").value(50.0))
				.andExpect(jsonPath("$.current.achieved").value(true))
				.andExpect(jsonPath("$.current.percent").value(100.0));
	}

	@Test
	void aConsumptionDropKeepsTheReferenceConsumptionAndStartsAtZero() throws Exception {
		mvc.perform(post(TARGETS).with(me()).contentType(MediaType.APPLICATION_JSON)
				.content(body("CONSUMPTION_DROP", 2025, "10", "2030-12-31", null)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.referenceValue").value(0))
				.andExpect(jsonPath("$.referenceConsumption").value(1000.0))
				// 800 MW against 1000: 20 % less, past the 10 % aimed at
				.andExpect(jsonPath("$.current.value").value(20.0))
				.andExpect(jsonPath("$.current.achieved").value(true));
	}

	@Test
	void theRecalculationAddsOneRowPerDayAndReplacesItTheSameDay() throws Exception {
		long id = create("60");

		recalculate.recalculateAll();
		recalculate.recalculateAll();

		assertThat(jdbc.queryForObject("SELECT count(*) FROM regional_target_progress WHERE target_id = ?", Long.class,
				id)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT day FROM regional_target_progress WHERE target_id = ?", LocalDate.class,
				id)).isEqualTo(LocalDate.now(java.time.ZoneId.of("Europe/Paris")));
	}

	@Test
	void anOfficialNeitherSeesNorReachesTheTargetsOfAnotherRegion() throws Exception {
		long id = create("60");

		// Same person, attached to another region since
		mvc.perform(get(TARGETS).with(official(user, "11")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
		mvc.perform(put(TARGETS + "/" + id).with(official(user, "11")).contentType(MediaType.APPLICATION_JSON)
				.content("{\"targetValue\": 70, \"deadline\": \"2030-12-31\", \"tolerance\": 2}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("TARGET_NOT_FOUND"));
		mvc.perform(delete(TARGETS + "/" + id).with(official(user, "11"))).andExpect(status().isNotFound());
		// Another official of the same region does not see it either
		mvc.perform(get(TARGETS).with(official(UUID.randomUUID().toString(), "93")))
				.andExpect(jsonPath("$.length()").value(0));
		mvc.perform(get(TARGETS).with(me())).andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	void aTargetIsCreatedInTheRegionOfTheTokenWhateverTheBodySays() throws Exception {
		mvc.perform(post(TARGETS).with(me()).contentType(MediaType.APPLICATION_JSON).content("""
				{"indicator": "RENEWABLE_SHARE", "referenceYear": 2025, "targetValue": 60, "deadline": "2030-12-31",
				 "regionCode": "11"}
				""")).andExpect(status().isCreated());

		assertThat(jdbc.queryForList("SELECT region_code FROM regional_target WHERE user_id = ?", String.class, user))
				.containsExactly("93");
	}

	@Test
	void updateChangesTheTargetAndKeepsTheReference() throws Exception {
		long id = create("60");

		mvc.perform(put(TARGETS + "/" + id).with(me()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"targetValue\": 70, \"deadline\": \"2032-12-31\", \"tolerance\": null}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.targetValue").value(70))
				.andExpect(jsonPath("$.deadline").value("2032-12-31"))
				.andExpect(jsonPath("$.tolerance").value((Object) null))
				.andExpect(jsonPath("$.referenceValue").value(40.0))
				.andExpect(jsonPath("$.current.percent").value(33.3));
	}

	@Test
	void deleteRemovesTheTargetAndItsHistoryAndTellsTheOtherModules() throws Exception {
		long id = create("60");

		mvc.perform(delete(TARGETS + "/" + id).with(me())).andExpect(status().isNoContent());

		assertThat(jdbc.queryForObject("SELECT count(*) FROM regional_target_progress WHERE target_id = ?", Long.class,
				id)).isZero();
		assertThat(events.stream(TargetDeleted.class)).containsExactly(new TargetDeleted(id));
		mvc.perform(delete(TARGETS + "/" + id).with(me())).andExpect(status().isNotFound());
	}

	@Test
	void invalidTargetsAreRefusedWithAStableCode() throws Exception {
		for (String invalid : List.of(
				body("SOLAR_ONLY", 2025, "60", "2030-12-31", null),
				body(null, 2025, "60", "2030-12-31", null),
				body("RENEWABLE_SHARE", 2012, "60", "2030-12-31", null),
				body("RENEWABLE_SHARE", LocalDate.now().getYear(), "60", "2030-12-31", null),
				body("RENEWABLE_SHARE", 2025, "120", "2030-12-31", null),
				body("RENEWABLE_SHARE", 2025, "60", "2020-01-01", null),
				body("RENEWABLE_SHARE", 2025, "60", "31/12/2030", null),
				body("RENEWABLE_SHARE", 2025, "60", "2030-12-31", "0"),
				body("RENEWABLE_SHARE", 2025, "40", "2030-12-31", null),
				"not json")) {
			mvc.perform(post(TARGETS).with(me()).contentType(MediaType.APPLICATION_JSON).content(invalid))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("INVALID_TARGET"));
		}
		assertThat(jdbc.queryForObject("SELECT count(*) FROM regional_target WHERE user_id = ?", Long.class, user))
				.isZero();
	}

	@Test
	void aReferenceYearWithoutDataIsRefused() throws Exception {
		when(readings.between(eq(PACA), any(), any())).thenReturn(List.of());

		mvc.perform(post(TARGETS).with(me()).contentType(MediaType.APPLICATION_JSON)
				.content(body("RENEWABLE_SHARE", 2025, "60", "2030-12-31", null)))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("REFERENCE_UNAVAILABLE"));
	}

	@Test
	void tenTargetsAtMost() throws Exception {
		for (int i = 0; i < 10; i++) {
			create(String.valueOf(50 + i));
		}

		mvc.perform(post(TARGETS).with(me()).contentType(MediaType.APPLICATION_JSON)
				.content(body("RENEWABLE_SHARE", 2025, "70", "2030-12-31", null)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("TARGET_LIMIT_REACHED"));
	}
}
