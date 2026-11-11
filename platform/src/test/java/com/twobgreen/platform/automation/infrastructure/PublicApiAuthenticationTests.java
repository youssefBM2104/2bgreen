package com.twobgreen.platform.automation.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.twobgreen.platform.TestcontainersConfiguration;
import com.twobgreen.platform.automation.domain.ApiKeyRepository;

/**
 * The security chain of {@code /api/v1/public/**} (U10): key in {@code X-API-Key}, quota per UTC day. Exercised on a
 * test endpoint, the public API of U7 does not exist yet.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class PublicApiAuthenticationTests {

	private static final String PROBE = "/api/v1/public/test/caller";

	private static final String KEYS = "/api/v1/automation/api-keys";

	private static final int QUOTA = 1000;

	private final String owner = UUID.randomUUID().toString();

	@Autowired
	MockMvc mvc;

	@Autowired
	JwtAuthenticationConverter converter;

	@Autowired
	ApiKeyRepository keys;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void validKeyGoesThroughWithItsQuota() throws Exception {
		String key = createKey();
		long id = keys.findByUserIdOrderById(owner).getFirst().id();
		long midnight = LocalDate.now(ZoneOffset.UTC).plusDays(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond();

		mvc.perform(get(PROBE).header("X-API-Key", key))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.keyId").value(id))
				.andExpect(jsonPath("$.userId").value(owner))
				.andExpect(header().string("X-RateLimit-Limit", "1000"))
				.andExpect(header().string("X-RateLimit-Remaining", "999"))
				.andExpect(header().string("X-RateLimit-Reset", Long.toString(midnight)));
		mvc.perform(get(PROBE).header("X-API-Key", key))
				.andExpect(header().string("X-RateLimit-Remaining", "998"));

		assertThat(keys.callsOn(id, LocalDate.now(ZoneOffset.UTC))).contains(2);
		assertThat(keys.findById(id).orElseThrow().lastUsedAt()).isNotNull();
		mvc.perform(get(KEYS).with(devops())).andExpect(jsonPath("$[0].callsToday").value(2))
				.andExpect(jsonPath("$[0].lastUsedAt").isNotEmpty());
	}

	@Test
	void missingKeyIsUnauthorized() throws Exception {
		mvc.perform(get(PROBE))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("API_KEY_REQUIRED"));
		mvc.perform(get(PROBE).header("X-API-Key", "  ")).andExpect(jsonPath("$.code").value("API_KEY_REQUIRED"));
	}

	@Test
	void unknownOrMalformedKeyIsUnauthorized() throws Exception {
		String unknown = "2bg_" + "A".repeat(43);
		for (String value : List.of("nope", unknown, createKey() + "x")) {
			mvc.perform(get(PROBE).header("X-API-Key", value))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.code").value("INVALID_API_KEY"))
					.andExpect(header().doesNotExist("X-RateLimit-Limit"));
		}
	}

	@Test
	void revokedKeyIsRefusedAtOnce() throws Exception {
		String key = createKey();
		long id = keys.findByUserIdOrderById(owner).getFirst().id();
		mvc.perform(get(PROBE).header("X-API-Key", key)).andExpect(status().isOk());

		mvc.perform(delete(KEYS + "/" + id).with(devops())).andExpect(status().isNoContent());

		mvc.perform(get(PROBE).header("X-API-Key", key))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_API_KEY"));
	}

	@Test
	void keycloakTokenAloneDoesNotOpenThePublicApi() throws Exception {
		mvc.perform(get(PROBE).with(devops())).andExpect(status().isUnauthorized());
		mvc.perform(get(PROBE).header("Authorization", "Bearer not-a-key")).andExpect(status().isUnauthorized());
	}

	@Test
	void aKeyDoesNotOpenTheKeycloakPaths() throws Exception {
		String key = createKey();

		mvc.perform(get(KEYS).header("X-API-Key", key)).andExpect(status().isUnauthorized());
		mvc.perform(post(KEYS).header("X-API-Key", key).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"by a key\"}")).andExpect(status().isUnauthorized());
	}

	@Test
	void publicApiAnswersNoBrowserPreflight() throws Exception {
		mvc.perform(options(PROBE).header("Origin", "http://localhost:4200")
				.header("Access-Control-Request-Method", "GET"))
				.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
	}

	@Test
	void aboveTheQuotaIsTooManyRequestsWithRetryAfter() throws Exception {
		String key = createKey();
		long id = keys.findByUserIdOrderById(owner).getFirst().id();
		useCalls(id, QUOTA - 1);

		mvc.perform(get(PROBE).header("X-API-Key", key))
				.andExpect(status().isOk())
				.andExpect(header().string("X-RateLimit-Remaining", "0"));
		var refused = mvc.perform(get(PROBE).header("X-API-Key", key))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("QUOTA_EXCEEDED"))
				.andExpect(header().string("X-RateLimit-Remaining", "0"))
				.andReturn().getResponse();

		long retryAfter = Long.parseLong(refused.getHeader("Retry-After"));
		long secondsToMidnight = LocalDate.now(ZoneOffset.UTC).plusDays(1).atStartOfDay(ZoneOffset.UTC)
				.toEpochSecond() - Instant.now().getEpochSecond();
		assertThat(retryAfter).isBetween(1L, secondsToMidnight + 1);
		// A refused call is not counted
		assertThat(keys.callsOn(id, LocalDate.now(ZoneOffset.UTC))).contains(QUOTA);
	}

	@Test
	void concurrentCallsGetExactlyTheCallsLeft() throws Exception {
		String key = createKey();
		long id = keys.findByUserIdOrderById(owner).getFirst().id();
		useCalls(id, QUOTA - 10);

		ConcurrentLinkedQueue<Integer> statuses = new ConcurrentLinkedQueue<>();
		List<Thread> threads = new ArrayList<>();
		for (int i = 0; i < 30; i++) {
			threads.add(Thread.ofVirtual().start(() -> {
				try {
					statuses.add(mvc.perform(get(PROBE).header("X-API-Key", key)).andReturn().getResponse()
							.getStatus());
				}
				catch (Exception e) {
					throw new IllegalStateException(e);
				}
			}));
		}
		for (Thread thread : threads) {
			thread.join();
		}

		assertThat(statuses).hasSize(30);
		assertThat(statuses.stream().filter(s -> s == 200).count()).isEqualTo(10);
		assertThat(statuses.stream().filter(s -> s == 429).count()).isEqualTo(20);
		assertThat(keys.callsOn(id, LocalDate.now(ZoneOffset.UTC))).contains(QUOTA);
	}

	@Test
	void theKeyNeverReachesTheLogs(CapturedOutput output) throws Exception {
		String key = createKey();
		mvc.perform(get(PROBE).header("X-API-Key", key)).andExpect(status().isOk());
		long id = keys.findByUserIdOrderById(owner).getFirst().id();
		mvc.perform(delete(KEYS + "/" + id).with(devops()));
		mvc.perform(get(PROBE).header("X-API-Key", key)).andExpect(status().isUnauthorized());

		assertThat(output.getAll()).doesNotContain(key.substring(4));
	}

	/** Puts the counter of today straight at {@code calls}. */
	private void useCalls(long id, int calls) {
		jdbc.update("INSERT INTO automation_api_key_daily_counter (api_key_id, day, call_count) VALUES (?, ?, ?)", id,
				LocalDate.now(ZoneOffset.UTC), calls);
	}

	private String createKey() throws Exception {
		MockHttpServletRequestBuilder request = post(KEYS).with(devops()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"ci\"}");
		return JsonPath.read(mvc.perform(request).andExpect(status().isCreated()).andReturn().getResponse()
				.getContentAsString(), "$.key");
	}

	private org.springframework.test.web.servlet.request.RequestPostProcessor devops() {
		return jwt().jwt(j -> j.subject(owner).claim("realm_access", Map.of("roles", List.of("devops"))))
				.authorities(token -> converter.convert(token).getAuthorities());
	}
}
