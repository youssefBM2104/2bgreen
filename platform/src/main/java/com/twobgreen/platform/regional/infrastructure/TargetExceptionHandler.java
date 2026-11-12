package com.twobgreen.platform.regional.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.twobgreen.platform.advisory.GridUnavailableException;
import com.twobgreen.platform.regional.application.InvalidTargetException;
import com.twobgreen.platform.regional.application.ManageTargets;
import com.twobgreen.platform.regional.application.ReferenceUnavailableException;
import com.twobgreen.platform.regional.application.RegionNotAssignedException;
import com.twobgreen.platform.regional.application.TargetLimitReachedException;
import com.twobgreen.platform.regional.application.TargetNotFoundException;

/**
 * Errors of {@link TargetController} as RFC 7807 Problem Details, with a stable {@code code}. No catch-all, as
 * everywhere in {@code platform}.
 */
@RestControllerAdvice(assignableTypes = TargetController.class)
class TargetExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(TargetExceptionHandler.class);

	// The message is written for the caller by the use case
	@ExceptionHandler(InvalidTargetException.class)
	ProblemDetail invalid(InvalidTargetException e) {
		return problem(HttpStatus.BAD_REQUEST, "Invalid target", e.getMessage(), "INVALID_TARGET");
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ProblemDetail unreadable(HttpMessageNotReadableException e) {
		return problem(HttpStatus.BAD_REQUEST, "Invalid target", "The body is not a valid target.", "INVALID_TARGET");
	}

	@ExceptionHandler(TargetLimitReachedException.class)
	ProblemDetail limit(TargetLimitReachedException e) {
		return problem(HttpStatus.CONFLICT, "Too many targets",
				"At most " + ManageTargets.MAX_TARGETS_PER_USER + " targets per official.", "TARGET_LIMIT_REACHED");
	}

	@ExceptionHandler(ReferenceUnavailableException.class)
	ProblemDetail noReference(ReferenceUnavailableException e) {
		return problem(HttpStatus.UNPROCESSABLE_CONTENT, "No reference", e.getMessage() + ". Choose another year.",
				"REFERENCE_UNAVAILABLE");
	}

	@ExceptionHandler(TargetNotFoundException.class)
	ProblemDetail notFound(TargetNotFoundException e) {
		return problem(HttpStatus.NOT_FOUND, "Not found", "No such target.", "TARGET_NOT_FOUND");
	}

	@ExceptionHandler(RegionNotAssignedException.class)
	ProblemDetail notAssigned(RegionNotAssignedException e) {
		return problem(HttpStatus.FORBIDDEN, "No region",
				"Your account is not attached to a region. Ask an administrator.", "REGION_NOT_ASSIGNED");
	}

	@ExceptionHandler(GridUnavailableException.class)
	ProblemDetail unavailable(GridUnavailableException e) {
		log.warn("Grid data unavailable for the targets: {}", e.getMessage());
		return problem(HttpStatus.SERVICE_UNAVAILABLE, "Grid data unavailable",
				"Grid data cannot be read right now. Try again later.", "GRID_UNAVAILABLE");
	}

	private static ProblemDetail problem(HttpStatus status, String title, String detail, String code) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		problem.setProperty("code", code);
		return problem;
	}
}
