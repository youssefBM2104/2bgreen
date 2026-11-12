package com.twobgreen.platform.regional.application;

/** The region has no reading yet. Normal on a fresh start, before the first regional poll. */
public class NoRegionalDataException extends RuntimeException {

	public NoRegionalDataException() {
		super("No reading is stored yet for the region");
	}
}
