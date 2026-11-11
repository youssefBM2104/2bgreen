package com.twobgreen.platform.automation.infrastructure;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import tools.jackson.databind.json.JsonMapper;

/**
 * The errors of the public API's security chain, written by hand because they happen in a filter, before any
 * controller advice. Same RFC 7807 shape and stable {@code code} as the other endpoints.
 */
final class ApiKeyProblems {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private ApiKeyProblems() {
	}

	static void missingKey(HttpServletResponse response) throws IOException {
		write(response, HttpStatus.UNAUTHORIZED, "API key required",
				"Send your API key in the " + ApiKeyAuthenticationFilter.HEADER + " header.", "API_KEY_REQUIRED");
	}

	/** Unknown and revoked keys get the same answer. */
	static void invalidKey(HttpServletResponse response) throws IOException {
		write(response, HttpStatus.UNAUTHORIZED, "Invalid API key", "This API key is unknown or has been revoked.",
				"INVALID_API_KEY");
	}

	static void quotaExceeded(HttpServletResponse response, int limit) throws IOException {
		write(response, HttpStatus.TOO_MANY_REQUESTS, "Quota exceeded", "This API key has used its " + limit
				+ " calls of the day. The quota starts again at 00:00 UTC.", "QUOTA_EXCEEDED");
	}

	private static void write(HttpServletResponse response, HttpStatus status, String title, String detail,
			String code) throws IOException {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("type", "about:blank");
		body.put("title", title);
		body.put("status", status.value());
		body.put("detail", detail);
		body.put("code", code);
		response.setStatus(status.value());
		if (status == HttpStatus.UNAUTHORIZED) {
			response.setHeader("WWW-Authenticate", "ApiKey header=\"" + ApiKeyAuthenticationFilter.HEADER + "\"");
		}
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		MAPPER.writeValue(response.getWriter(), body);
	}
}
