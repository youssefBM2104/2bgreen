package com.twobgreen.gridintelligence.infrastructure.messaging;

/**
 * A message that does not respect contract K1. The stream logs it and skips it.
 */
public class InvalidSnapshotMessageException extends RuntimeException {

	InvalidSnapshotMessageException(String message) {
		super(message);
	}

	InvalidSnapshotMessageException(String message, Throwable cause) {
		super(message, cause);
	}
}
