package com.twobgreen.platform.automation.application;

/**
 * A key just created, with the key in full. It is given to the user once and never stored.
 *
 * @param secret the key itself, to show once
 */
public record NewApiKey(ApiKeyUsage usage, String secret) {

	/** Keeps the key out of logs and error messages that print this record. */
	@Override
	public String toString() {
		return "NewApiKey[usage=" + usage + ", secret=***]";
	}
}
