package com.twobgreen.platform.automation.infrastructure;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import com.twobgreen.platform.automation.application.ApiKeyCheck;
import com.twobgreen.platform.automation.application.CheckApiKey;

/**
 * Authenticates a call of {@code /api/v1/public/**} by its {@code X-API-Key} header (U10), and applies the daily
 * quota. A call that goes through carries the {@code X-RateLimit-*} headers; above the quota the answer is 429 with
 * {@code Retry-After}. The key is never logged, nor kept anywhere once its hash is computed.
 */
class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

	static final String HEADER = "X-API-Key";

	static final String AUTHORITY = "ROLE_api_key";

	private final CheckApiKey checkApiKey;

	/** Keeps the caller for the error dispatch, which runs on its own request. */
	private final SecurityContextRepository contexts = new RequestAttributeSecurityContextRepository();

	ApiKeyAuthenticationFilter(CheckApiKey checkApiKey) {
		this.checkApiKey = checkApiKey;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String presented = request.getHeader(HEADER);
		if (presented == null || presented.isBlank()) {
			ApiKeyProblems.missingKey(response);
			return;
		}
		switch (checkApiKey.check(presented.strip())) {
			case ApiKeyCheck.Refused refused -> ApiKeyProblems.invalidKey(response);
			case ApiKeyCheck.QuotaExceeded exceeded -> {
				writeQuota(response, exceeded.quota());
				response.setHeader("Retry-After", Long.toString(secondsUntil(exceeded.quota().resetAt())));
				ApiKeyProblems.quotaExceeded(response, exceeded.quota().limit());
			}
			case ApiKeyCheck.Accepted accepted -> {
				writeQuota(response, accepted.quota());
				SecurityContext context = SecurityContextHolder.createEmptyContext();
				context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
						new ApiKeyCaller(accepted.keyId(), accepted.userId()), null,
						List.of(new SimpleGrantedAuthority(AUTHORITY))));
				SecurityContextHolder.setContext(context);
				contexts.saveContext(context, request, response);
				chain.doFilter(request, response);
			}
		}
	}

	private static void writeQuota(HttpServletResponse response, ApiKeyCheck.Quota quota) {
		response.setHeader("X-RateLimit-Limit", Integer.toString(quota.limit()));
		response.setHeader("X-RateLimit-Remaining", Integer.toString(quota.remaining()));
		// Seconds since the epoch, as GitHub and most public APIs give it
		response.setHeader("X-RateLimit-Reset", Long.toString(quota.resetAt().getEpochSecond()));
	}

	/** Rounded up, and at least 1: a client that waits that long is sure to be past midnight UTC. */
	private static long secondsUntil(Instant resetAt) {
		Duration left = Duration.between(Instant.now(), resetAt);
		long seconds = left.toSeconds() + (left.toNanosPart() > 0 ? 1 : 0);
		return Math.max(1, seconds);
	}
}
