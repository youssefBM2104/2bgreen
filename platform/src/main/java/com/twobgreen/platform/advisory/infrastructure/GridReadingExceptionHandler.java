package com.twobgreen.platform.advisory.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.twobgreen.platform.advisory.GridUnavailableException;

/**
 * Errors of {@link TrafficLightController} as RFC 7807 Problem Details, with a stable {@code code} like the read API.
 * No catch-all here: it would also catch the access-denied exception of {@code @PreAuthorize} and turn a 403 into
 * a 500.
 */
@RestControllerAdvice(assignableTypes = TrafficLightController.class)
class GridReadingExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GridReadingExceptionHandler.class);

	@ExceptionHandler(NoGridReadingException.class)
	ProblemDetail noData(NoGridReadingException e) {
		return problem(HttpStatus.NOT_FOUND, "No data", "No grid reading is available yet.", "NO_DATA");
	}

	// Never put the exception message in the body: it is for the logs, the frontend has no use for internals
	@ExceptionHandler(GridUnavailableException.class)
	ProblemDetail unavailable(GridUnavailableException e) {
		log.warn("Grid data unavailable: {}", e.getMessage());
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
