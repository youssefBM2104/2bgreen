package com.twobgreen.platform.advisory.infrastructure;

/** Nothing to show yet: no reading is stored. Normal on a fresh start. */
class NoGridReadingException extends RuntimeException {

	NoGridReadingException() {
		super("No grid reading is stored yet");
	}
}
