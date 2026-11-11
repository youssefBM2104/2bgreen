package com.twobgreen.platform.automation.infrastructure;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.twobgreen.platform.automation.application.AlertRuleLimitReachedException;
import com.twobgreen.platform.automation.application.AlertRuleNotFoundException;
import com.twobgreen.platform.automation.application.ManageAlertRules;

/**
 * Errors of {@link AlertRuleController} as RFC 7807 Problem Details with a stable {@code code}, like the other
 * endpoints. No catch-all here: it would also catch the access-denied exception of {@code @PreAuthorize} and turn a
 * 403 into a 500.
 */
@RestControllerAdvice(assignableTypes = AlertRuleController.class)
class AlertRuleExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ProblemDetail invalid(MethodArgumentNotValidException e) {
		return problem(HttpStatus.BAD_REQUEST, "Invalid alert", "threshold is required and must be between "
				+ ManageAlertRules.MIN_THRESHOLD + " and " + ManageAlertRules.MAX_THRESHOLD
				+ " gCO2eq/kWh; active must be true or false.", "INVALID_ALERT");
	}

	@ExceptionHandler(AlertRuleNotFoundException.class)
	ProblemDetail notFound(AlertRuleNotFoundException e) {
		return problem(HttpStatus.NOT_FOUND, "Alert not found", "You have no alert with this id.",
				"ALERT_NOT_FOUND");
	}

	@ExceptionHandler(AlertRuleLimitReachedException.class)
	ProblemDetail limitReached(AlertRuleLimitReachedException e) {
		return problem(HttpStatus.CONFLICT, "Alert limit reached", "You already have "
				+ ManageAlertRules.MAX_RULES_PER_USER + " alerts. Delete one to create another.",
				"ALERT_LIMIT_REACHED");
	}

	private static ProblemDetail problem(HttpStatus status, String title, String detail, String code) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		problem.setProperty("code", code);
		return problem;
	}
}
