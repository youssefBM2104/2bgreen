package com.twobgreen.platform.regional.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.twobgreen.platform.advisory.GridUnavailableException;
import com.twobgreen.platform.regional.application.NoRegionalDataException;
import com.twobgreen.platform.regional.application.RegionNotAssignedException;

/**
 * Errors of {@link RegionalDashboardController} as RFC 7807 Problem Details, with a stable {@code code}. No
 * catch-all, as everywhere in {@code platform}: it would turn the 403 of {@code @PreAuthorize} into a 500.
 */
@RestControllerAdvice(assignableTypes = RegionalDashboardController.class)
class RegionalDashboardExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(RegionalDashboardExceptionHandler.class);

	// A 403 and not a 400: the request is fine, the account is not set up for this page
	@ExceptionHandler(RegionNotAssignedException.class)
	ProblemDetail notAssigned(RegionNotAssignedException e) {
		log.warn("Regional dashboard refused: the token has no known region_code");
		return problem(HttpStatus.FORBIDDEN, "No region",
				"Your account is not attached to a region. Ask an administrator.", "REGION_NOT_ASSIGNED");
	}

	@ExceptionHandler(NoRegionalDataException.class)
	ProblemDetail noData(NoRegionalDataException e) {
		return problem(HttpStatus.NOT_FOUND, "No data", "No reading is available yet for your region.", "NO_DATA");
	}

	@ExceptionHandler(GridUnavailableException.class)
	ProblemDetail unavailable(GridUnavailableException e) {
		log.warn("Grid data unavailable for the regional dashboard: {}", e.getMessage());
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
