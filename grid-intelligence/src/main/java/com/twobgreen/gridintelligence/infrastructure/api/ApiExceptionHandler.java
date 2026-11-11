package com.twobgreen.gridintelligence.infrastructure.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.twobgreen.gridintelligence.application.InvalidRangeException;
import com.twobgreen.gridintelligence.application.NoSnapshotException;
import com.twobgreen.gridintelligence.application.UnknownScopeException;

/**
 * Errors of contract K2 as RFC 7807 Problem Details, with a stable {@code code} that clients switch on. The texts are
 * those of {@code docs/api/examples/error-*.json}. Spring's own errors (unknown route, wrong method, bad media type)
 * are handled by the base class and keep their real status: they are outside the contract and carry no {@code code}.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(NoSnapshotException.class)
	ProblemDetail noData(NoSnapshotException e) {
		return problem(HttpStatus.NOT_FOUND, "No data", "No snapshot with measures has been stored yet.", "NO_DATA");
	}

	// The message is written by the use case and never quotes the input
	@ExceptionHandler(InvalidRangeException.class)
	ProblemDetail invalidParameter(InvalidRangeException e) {
		return problem(HttpStatus.BAD_REQUEST, "Invalid parameter", e.getMessage(), "INVALID_PARAMETER");
	}

	@ExceptionHandler(UnknownScopeException.class)
	ProblemDetail scopeUnknown(UnknownScopeException e) {
		return problem(HttpStatus.NOT_FOUND, "Unknown scope", "Region '" + shown(e.code()) + "' is not recognised.",
				"SCOPE_UNKNOWN");
	}

	// The database is down or too busy (the connection pool gave up after its timeout)
	@ExceptionHandler({ DataAccessResourceFailureException.class, TransientDataAccessException.class })
	ProblemDetail unavailable(Exception e) {
		log.warn("Database unavailable: {}", e.toString());
		return problem(HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable", "The database is not reachable.",
				"SERVICE_UNAVAILABLE");
	}

	// Never put the exception message in the body: it may reveal internals of an unauthenticated service
	@ExceptionHandler(Exception.class)
	ProblemDetail unexpected(Exception e) {
		log.error("Unexpected error in the read API", e);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error", "Unexpected error.", "INTERNAL_ERROR");
	}

	// The code comes from the URL: kept short, it is only there to help the caller find their mistake
	private static String shown(String code) {
		String text = code == null ? "" : code;
		return text.length() <= 20 ? text : text.substring(0, 20) + "...";
	}

	private static ProblemDetail problem(HttpStatus status, String title, String detail, String code) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		problem.setProperty("code", code);
		return problem;
	}
}
