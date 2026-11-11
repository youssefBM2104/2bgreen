package com.twobgreen.platform.automation.infrastructure;

import java.security.SecureRandom;
import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.twobgreen.platform.automation.application.CheckApiKey;
import com.twobgreen.platform.automation.application.ManageApiKeys;
import com.twobgreen.platform.automation.domain.ApiKeyRepository;

/**
 * API keys (U10): the use cases, and the security chain of {@code /api/v1/public/**}.
 * <p>
 * That chain is separate from the Keycloak one ({@code config.SecurityConfig}) and comes before it. It knows only
 * the {@code X-API-Key} header: no JWT (a Keycloak token alone gets a 401 there), no CORS (the public API is for
 * scripts and CI pipelines, not for a browser page), no session. Every other path stays on the Keycloak chain.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ApiKeyProperties.class)
class ApiKeyConfig {

	static final String PUBLIC_API = "/api/v1/public/**";

	@Bean
	ManageApiKeys manageApiKeys(ApiKeyRepository keys, PlatformTransactionManager transactionManager,
			ApiKeyProperties properties) {
		return new ManageApiKeys(keys, new TransactionTemplate(transactionManager), new SecureRandom(),
				Clock.systemUTC(), properties.dailyQuota());
	}

	@Bean
	CheckApiKey checkApiKey(ApiKeyRepository keys, ApiKeyProperties properties) {
		return new CheckApiKey(keys, Clock.systemUTC(), properties.dailyQuota());
	}

	/**
	 * The filter is created here and not declared as a bean: Spring Boot would also register a filter bean for every
	 * request of the application, Keycloak paths included.
	 */
	@Bean
	@Order(1)
	SecurityFilterChain publicApiSecurityFilterChain(HttpSecurity http, CheckApiKey checkApiKey) throws Exception {
		http
				.securityMatcher(PUBLIC_API)
				.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
				.addFilterBefore(new ApiKeyAuthenticationFilter(checkApiKey), AnonymousAuthenticationFilter.class)
				.exceptionHandling(errors -> errors
						.authenticationEntryPoint((request, response, e) -> ApiKeyProblems.missingKey(response)))
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.csrf(csrf -> csrf.disable())
				.cors(cors -> cors.disable());
		return http.build();
	}
}
