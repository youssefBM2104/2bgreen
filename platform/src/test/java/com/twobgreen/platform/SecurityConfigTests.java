package com.twobgreen.platform;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTests {

	private static final String ORIGIN = "http://localhost:4200";

	@Autowired
	MockMvc mvc;

	@Autowired
	JwtAuthenticationConverter converter;

	// Goes through the real converter so the realm_access mapping is what gets tested
	private JwtRequestPostProcessor tokenWithRole(String role) {
		return jwt().jwt(j -> j.claim("realm_access", Map.of("roles", List.of(role))))
				.authorities(token -> converter.convert(token).getAuthorities());
	}

	@Test
	void withoutTokenIsUnauthorized() throws Exception {
		mvc.perform(get("/test/citizen")).andExpect(status().isUnauthorized());
	}

	@Test
	void wrongRoleIsForbidden() throws Exception {
		mvc.perform(get("/test/devops").with(tokenWithRole("citizen"))).andExpect(status().isForbidden());
	}

	@Test
	void citizenRoleIsAllowed() throws Exception {
		mvc.perform(get("/test/citizen").with(tokenWithRole("citizen"))).andExpect(status().isOk());
	}

	@Test
	void devopsRoleIsAllowed() throws Exception {
		mvc.perform(get("/test/devops").with(tokenWithRole("devops"))).andExpect(status().isOk());
	}

	@Test
	void regionalOfficialRoleIsAllowed() throws Exception {
		mvc.perform(get("/test/elu").with(tokenWithRole("regional_official"))).andExpect(status().isOk());
	}

	@Test
	void healthIsPublic() throws Exception {
		mvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}

	@Test
	void infoRequiresToken() throws Exception {
		mvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
	}

	@Test
	void tokenWithoutRealmAccessIsForbidden() throws Exception {
		mvc.perform(get("/test/citizen").with(jwt().authorities(token -> converter.convert(token).getAuthorities()))).andExpect(status().isForbidden());
	}

	@Test
	void preflightFromFrontendOriginIsAllowed() throws Exception {
		mvc.perform(options("/test/citizen")
				.header(HttpHeaders.ORIGIN, ORIGIN)
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN));
	}

	@Test
	void preflightFromOtherOriginIsRejected() throws Exception {
		mvc.perform(options("/test/citizen")
				.header(HttpHeaders.ORIGIN, "http://evil.example")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
				.andExpect(status().isForbidden());
	}

}
