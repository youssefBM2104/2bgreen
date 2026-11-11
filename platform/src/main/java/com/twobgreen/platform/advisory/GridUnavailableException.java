package com.twobgreen.platform.advisory;

/** The source of grid data cannot answer: unreachable, timeout, server error or unreadable response. */
public class GridUnavailableException extends RuntimeException {

	public GridUnavailableException(String message) {
		super(message);
	}

	public GridUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}
}
