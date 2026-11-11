package com.twobgreen.platform.automation.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.twobgreen.platform.TestcontainersConfiguration;
import com.twobgreen.platform.automation.domain.AlertRule;
import com.twobgreen.platform.automation.domain.AlertRuleRepository;

/** The alerts API as the frontend sees it, through the real security chain and the real database. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AlertRuleControllerTests {

	private static final String ALERTS = "/api/v1/automation/alerts";

	private final String alice = UUID.randomUUID().toString();

	private final String bob = UUID.randomUUID().toString();

	@Autowired
	MockMvc mvc;

	@Autowired
	JwtAuthenticationConverter converter;

	@Autowired
	AlertRuleRepository alertRules;

	@Test
	void withoutTokenIsUnauthorized() throws Exception {
		mvc.perform(get(ALERTS)).andExpect(status().isUnauthorized());
		mvc.perform(post(ALERTS).contentType(MediaType.APPLICATION_JSON).content("{\"threshold\":30}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void otherRolesAreForbidden() throws Exception {
		for (String role : List.of("devops", "regional_official")) {
			mvc.perform(get(ALERTS).with(token(alice, role))).andExpect(status().isForbidden());
			mvc.perform(post(ALERTS).with(token(alice, role)).contentType(MediaType.APPLICATION_JSON)
					.content("{\"threshold\":30}")).andExpect(status().isForbidden());
		}
		assertThat(alertRules.countByUserId(alice)).isZero();
	}

	@Test
	void citizenCreatesListsChangesAndDeletesTheirAlert() throws Exception {
		mvc.perform(get(ALERTS).with(citizen(alice))).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));

		long id = create(alice, 30);
		AlertRule stored = alertRules.findById(id).orElseThrow();
		assertThat(stored.userId()).isEqualTo(alice);
		assertThat(stored.scope()).isEqualTo("national");
		assertThat(stored.active()).isTrue();

		mvc.perform(get(ALERTS).with(citizen(alice)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].id").value(id))
				.andExpect(jsonPath("$[0].threshold").value(30))
				.andExpect(jsonPath("$[0].active").value(true))
				.andExpect(jsonPath("$[0].createdAt").value(stored.createdAt().toString()))
				// The owner comes from the token and is not echoed
				.andExpect(jsonPath("$[0].userId").doesNotExist());

		mvc.perform(put(ALERTS + "/" + id).with(citizen(alice)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"threshold\":20,\"active\":false}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.threshold").value(20))
				.andExpect(jsonPath("$.active").value(false));
		assertThat(alertRules.findById(id).orElseThrow().threshold()).isEqualTo(20);

		mvc.perform(delete(ALERTS + "/" + id).with(citizen(alice))).andExpect(status().isNoContent());
		assertThat(alertRules.findById(id)).isEmpty();
		mvc.perform(delete(ALERTS + "/" + id).with(citizen(alice))).andExpect(status().isNotFound());
	}

	@Test
	void userIdInTheBodyIsIgnored() throws Exception {
		mvc.perform(post(ALERTS).with(citizen(alice)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"threshold\":30,\"userId\":\"" + bob + "\",\"scope\":\"region:32\"}"))
				.andExpect(status().isCreated());

		assertThat(alertRules.countByUserId(bob)).isZero();
		assertThat(alertRules.findByUserIdOrderById(alice)).singleElement()
				.satisfies(rule -> assertThat(rule.scope()).isEqualTo("national"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "{\"threshold\":0}", "{\"threshold\":1001}", "{\"threshold\":-5}", "{}",
			"{\"threshold\":null}" })
	void thresholdOutsideOneToThousandIsRefused(String body) throws Exception {
		mvc.perform(post(ALERTS).with(citizen(alice)).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("INVALID_ALERT"));

		assertThat(alertRules.countByUserId(alice)).isZero();
	}

	@Test
	void limitsOfTheRangeAreAccepted() throws Exception {
		create(alice, 1);
		create(alice, 1000);
	}

	@Test
	void changeNeedsBothFieldsAndAValidThreshold() throws Exception {
		long id = create(alice, 30);

		for (String body : List.of("{\"threshold\":20}", "{\"active\":false}",
				"{\"threshold\":5000,\"active\":true}")) {
			mvc.perform(put(ALERTS + "/" + id).with(citizen(alice)).contentType(MediaType.APPLICATION_JSON)
					.content(body))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("INVALID_ALERT"));
		}
		assertThat(alertRules.findById(id).orElseThrow().threshold()).isEqualTo(30);
	}

	@Test
	void sixthAlertIsRefused() throws Exception {
		for (int i = 1; i <= 5; i++) {
			create(alice, 10 * i);
		}

		mvc.perform(post(ALERTS).with(citizen(alice)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"threshold\":60}"))
				.andExpect(status().isConflict())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("ALERT_LIMIT_REACHED"));
		assertThat(alertRules.countByUserId(alice)).isEqualTo(5);

		// The limit is per user, and deleting one frees a place
		create(bob, 60);
		long first = alertRules.findByUserIdOrderById(alice).getFirst().id();
		mvc.perform(delete(ALERTS + "/" + first).with(citizen(alice))).andExpect(status().isNoContent());
		create(alice, 60);
	}

	@Test
	void userCanNeitherReadNorChangeTheAlertOfAnother() throws Exception {
		long id = create(alice, 30);

		mvc.perform(get(ALERTS).with(citizen(bob))).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
		mvc.perform(put(ALERTS + "/" + id).with(citizen(bob)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"threshold\":999,\"active\":false}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("ALERT_NOT_FOUND"));
		mvc.perform(delete(ALERTS + "/" + id).with(citizen(bob)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("ALERT_NOT_FOUND"));

		AlertRule untouched = alertRules.findById(id).orElseThrow();
		assertThat(untouched.threshold()).isEqualTo(30);
		assertThat(untouched.active()).isTrue();
	}

	private long create(String user, int threshold) throws Exception {
		ResultActions result = mvc.perform(post(ALERTS).with(citizen(user)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"threshold\":" + threshold + "}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.threshold").value(threshold))
				.andExpect(jsonPath("$.active").value(true));
		long id = alertRules.findByUserIdOrderById(user).getLast().id();
		result.andExpect(jsonPath("$.id").value(id)).andExpect(header().string("Location", ALERTS + "/" + id));
		return id;
	}

	private JwtRequestPostProcessor citizen(String user) {
		return token(user, "citizen");
	}

	// Goes through the real converter, and the subject is what identifies the user
	private JwtRequestPostProcessor token(String user, String role) {
		return jwt().jwt(j -> j.subject(user).claim("realm_access", Map.of("roles", List.of(role))))
				.authorities(token -> converter.convert(token).getAuthorities());
	}
}
