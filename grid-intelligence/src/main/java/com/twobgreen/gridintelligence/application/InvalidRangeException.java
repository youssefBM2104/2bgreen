package com.twobgreen.gridintelligence.application;

/**
 * The caller's {@code from} / {@code to} cannot be served: missing, not a UTC date-time, not in order, or wider than
 * {@link GetSnapshotRange#MAX_RANGE} (K2: {@code INVALID_PARAMETER}). The message is written by us, never quotes the
 * input, and is safe to send back.
 */
public class InvalidRangeException extends RuntimeException {

	public InvalidRangeException(String message) {
		super(message);
	}
}
