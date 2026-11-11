package com.twobgreen.platform.automation.application;

/** The user already has {@link ManageApiKeys#MAX_ACTIVE_KEYS_PER_USER} active keys. */
public class ApiKeyLimitReachedException extends RuntimeException {

	public ApiKeyLimitReachedException() {
		super("Limit of " + ManageApiKeys.MAX_ACTIVE_KEYS_PER_USER + " active API keys reached");
	}
}
