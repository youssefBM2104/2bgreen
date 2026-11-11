package com.twobgreen.platform.automation.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.twobgreen.platform.TestcontainersConfiguration;
import com.twobgreen.platform.automation.domain.ApiKey;
import com.twobgreen.platform.automation.domain.ApiKeyRepository;

/** The key management API as the DevOps page sees it, through the real security chain and the real database. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ApiKeyControllerTests {

	private static final String KEYS = "/api/v1/automation/api-keys";

	private final String alice = UUID.randomUUID().toString();

	private final String bob = UUID.randomUUID().toString();

	@Autowired
	MockMvc mvc;

	@Autowired
	JwtAuthenticationConverter converter;

	@Autowired
	ApiKeyRepository keys;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void withoutTokenIsUnauthorized() throws Exception {
		mvc.perform(get(KEYS)).andExpect(status().isUnauthorized());
		mvc.perform(post(KEYS).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"ci\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void otherRolesAreForbidden() throws Exception {
		for (String role : List.of("citizen", "regional_official")) {
			mvc.perform(get(KEYS).with(token(alice, role))).andExpect(status().isForbidden());
			mvc.perform(post(KEYS).with(token(alice, role)).contentType(MediaType.APPLICATION_JSON)
					.content("{\"name\":\"ci\"}")).andExpect(status().isForbidden());
		}
		assertThat(keys.findByUserIdOrderById(alice)).isEmpty();
	}

	@Test
	void creationShowsTheKeyOnceAndStoresOnlyItsHash() throws Exception {
		String body = mvc.perform(post(KEYS).with(devops(alice)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"  GitHub Actions  \"}"))
				.andExpect(status().isCreated())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.name").value("GitHub Actions"))
				.andExpect(jsonPath("$.callsToday").value(0))
				.andExpect(jsonPath("$.dailyQuota").value(1000))
				.andExpect(jsonPath("$.keyHash").doesNotExist())
				.andReturn().getResponse().getContentAsString();
		String key = JsonPath.read(body, "$.key");
		long id = ((Number) JsonPath.read(body, "$.id")).longValue();

		assertThat(key).matches("2bg_[A-Za-z0-9_-]{43}");
		assertThat((String) JsonPath.read(body, "$.prefix")).isEqualTo(key.substring(0, 8));
		ApiKey stored = keys.findById(id).orElseThrow();
		assertThat(stored.userId()).isEqualTo(alice);
		assertThat(stored.keyHash()).isEqualTo(sha256(key));
		// The key appears in no column of its row, nor anywhere in the table
		assertThat(jdbc.queryForObject("SELECT count(*) FROM automation_api_key WHERE row(automation_api_key.*)::text"
				+ " LIKE '%' || ? || '%'", Long.class, key.substring(8))).isZero();

		mvc.perform(get(KEYS).with(devops(alice)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].id").value(id))
				.andExpect(jsonPath("$[0].prefix").value(key.substring(0, 8)))
				.andExpect(jsonPath("$[0].key").doesNotExist())
				.andExpect(jsonPath("$[0].keyHash").doesNotExist())
				.andExpect(jsonPath("$[0].lastUsedAt").isEmpty());
	}

	@Test
	void twoKeysNeverShareAValue() throws Exception {
		assertThat(create(alice, "a")).isNotEqualTo(create(alice, "b"));
	}

	@Test
	void revokedKeyLeavesTheListAndFreesItsPlace() throws Exception {
		for (int i = 0; i < 5; i++) {
			create(alice, "key " + i);
		}
		mvc.perform(post(KEYS).with(devops(alice)).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"6\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("API_KEY_LIMIT_REACHED"));

		long first = keys.findByUserIdOrderById(alice).getFirst().id();
		mvc.perform(delete(KEYS + "/" + first).with(devops(alice))).andExpect(status().isNoContent());
		// Twice is fine, and keeps the first date
		var revokedAt = keys.findById(first).orElseThrow().revokedAt();
		mvc.perform(delete(KEYS + "/" + first).with(devops(alice))).andExpect(status().isNoContent());
		assertThat(keys.findById(first).orElseThrow().revokedAt()).isNotNull().isEqualTo(revokedAt);

		mvc.perform(get(KEYS).with(devops(alice))).andExpect(jsonPath("$", hasSize(4)));
		create(alice, "replacement");
	}

	@Test
	void anotherUsersKeyIsNotFound() throws Exception {
		create(alice, "ci");
		long id = keys.findByUserIdOrderById(alice).getFirst().id();

		mvc.perform(get(KEYS).with(devops(bob))).andExpect(jsonPath("$", hasSize(0)));
		mvc.perform(delete(KEYS + "/" + id).with(devops(bob)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("API_KEY_NOT_FOUND"));
		assertThat(keys.findById(id).orElseThrow().revokedAt()).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = { "{}", "{\"name\":\"\"}", "{\"name\":\"   \"}",
			"{\"name\":\"0123456789012345678901234567890123456789012345678901234567890\"}" })
	void invalidNameIsRefused(String body) throws Exception {
		mvc.perform(post(KEYS).with(devops(alice)).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_API_KEY_REQUEST"));
		assertThat(keys.findByUserIdOrderById(alice)).isEmpty();
	}

	@Test
	void concurrentCreationsCannotPassTheLimit() throws Exception {
		List<Thread> threads = new java.util.ArrayList<>();
		for (int i = 0; i < 12; i++) {
			int n = i;
			threads.add(Thread.ofVirtual().start(() -> {
				try {
					mvc.perform(post(KEYS).with(devops(alice)).contentType(MediaType.APPLICATION_JSON)
							.content("{\"name\":\"k" + n + "\"}"));
				}
				catch (Exception e) {
					throw new IllegalStateException(e);
				}
			}));
		}
		for (Thread thread : threads) {
			thread.join();
		}
		assertThat(keys.countByUserIdAndRevokedAtIsNull(alice)).isEqualTo(5);
	}

	/** Creates a key and returns it in full. */
	private String create(String user, String name) throws Exception {
		String body = mvc.perform(post(KEYS).with(devops(user)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"" + name + "\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.key");
	}

	private JwtRequestPostProcessor devops(String user) {
		return token(user, "devops");
	}

	private JwtRequestPostProcessor token(String user, String role) {
		return jwt().jwt(j -> j.subject(user).claim("realm_access", Map.of("roles", List.of(role))))
				.authorities(token -> converter.convert(token).getAuthorities());
	}

	static String sha256(String value) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(value.getBytes(StandardCharsets.UTF_8)));
	}
}
