package com.twobgreen.gridintelligence.application;

/**
 * ODRÉ could not be read. The reason tells the caller what to do: wait and retry, back off, or alert.
 * Messages never contain response bodies.
 */
public class OdreException extends RuntimeException {

	public enum Reason {
		/** No response in time, or no connection. Transient: retry on the next cycle. */
		UNREACHABLE,
		/** HTTP 429: the call quota (50,000 per month) is used up. Back off. */
		QUOTA_EXCEEDED,
		/** HTTP 5xx: ODRÉ is failing. Transient: retry later. */
		SERVER_ERROR,
		/** Other HTTP 4xx: ODRÉ refused our request, so the bug is on our side. */
		REJECTED,
		/** HTTP 200 with a body that is not the expected JSON: ODRÉ changed its format. */
		INVALID_RESPONSE
	}

	private final Reason reason;

	public OdreException(Reason reason, String message) {
		super(message);
		this.reason = reason;
	}

	public OdreException(Reason reason, String message, Throwable cause) {
		super(message, cause);
		this.reason = reason;
	}

	public Reason reason() {
		return reason;
	}
}
