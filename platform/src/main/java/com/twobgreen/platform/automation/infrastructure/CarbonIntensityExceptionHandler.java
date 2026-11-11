package com.twobgreen.platform.automation.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.twobgreen.platform.advisory.GridUnavailableException;
import com.twobgreen.platform.automation.application.InvalidIntensityRequestException;

/**
 * Errors of {@link CarbonIntensityController} as RFC 7807 Problem Details, with the stable {@code code} of the public
 * contract. Messages of internal exceptions never reach the body. No catch-all, as everywhere in {@code platform}.
 */
@RestControllerAdvice(assignableTypes = CarbonIntensityController.class)
class CarbonIntensityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(CarbonIntensityExceptionHandler.class);

	// The message is written for the caller by the use case, it holds no internals
	@ExceptionHandler(InvalidIntensityRequestException.class)
	ProblemDetail invalid(InvalidIntensityRequestException e) {
		return problem(HttpStatus.BAD_REQUEST, "Invalid parameter", e.getMessage(), "INVALID_PARAMETER");
	}

	@ExceptionHandler(CarbonIntensityController.NoIntensityException.class)
	ProblemDetail noData(CarbonIntensityController.NoIntensityException e) {
		return problem(HttpStatus.NOT_FOUND, "No data", "No reading is available yet for this scope.", "NO_DATA");
	}

	@ExceptionHandler(GridUnavailableException.class)
	ProblemDetail unavailable(GridUnavailableException e) {
		log.warn("Grid data unavailable for the public API: {}", e.getMessage());
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
