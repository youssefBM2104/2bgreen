package com.twobgreen.platform.regional.application;

/** A target that cannot be stored as asked. The message says why, for the caller: it holds no internals. */
public class InvalidTargetException extends RuntimeException {

	public InvalidTargetException(String message) {
		super(message);
	}
}
