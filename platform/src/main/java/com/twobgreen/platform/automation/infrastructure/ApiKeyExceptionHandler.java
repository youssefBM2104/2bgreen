package com.twobgreen.platform.automation.infrastructure;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.twobgreen.platform.automation.application.ApiKeyLimitReachedException;
import com.twobgreen.platform.automation.application.ApiKeyNotFoundException;
import com.twobgreen.platform.automation.application.ManageApiKeys;

/**
 * Errors of {@link ApiKeyController} as RFC 7807 Problem Details with a stable {@code code}. No catch-all here: it
 * would also catch the access-denied exception of {@code @PreAuthorize} and turn a 403 into a 500.
 */
@RestControllerAdvice(assignableTypes = ApiKeyController.class)
class ApiKeyExceptionHandler {

	@ExceptionHandler({ MethodArgumentNotValidException.class, IllegalArgumentException.class })
	ProblemDetail invalid(Exception e) {
		return problem(HttpStatus.BAD_REQUEST, "Invalid API key request",
				"name is required and must have 1 to " + ManageApiKeys.MAX_NAME_LENGTH + " characters.",
				"INVALID_API_KEY_REQUEST");
	}

	@ExceptionHandler(ApiKeyNotFoundException.class)
	ProblemDetail notFound(ApiKeyNotFoundException e) {
		return problem(HttpStatus.NOT_FOUND, "API key not found", "You have no API key with this id.",
				"API_KEY_NOT_FOUND");
	}

	@ExceptionHandler(ApiKeyLimitReachedException.class)
	ProblemDetail limitReached(ApiKeyLimitReachedException e) {
		return problem(HttpStatus.CONFLICT, "API key limit reached", "You already have "
				+ ManageApiKeys.MAX_ACTIVE_KEYS_PER_USER + " active API keys. Revoke one to create another.",
				"API_KEY_LIMIT_REACHED");
	}

	private static ProblemDetail problem(HttpStatus status, String title, String detail, String code) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		problem.setProperty("code", code);
		return problem;
	}
}
