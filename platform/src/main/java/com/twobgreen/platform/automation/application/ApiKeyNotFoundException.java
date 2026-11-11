package com.twobgreen.platform.automation.application;

/** The user has no API key with this id. Another user's key is reported the same way. */
public class ApiKeyNotFoundException extends RuntimeException {

	public ApiKeyNotFoundException(long id) {
		super("No API key " + id + " for this user");
	}
}
