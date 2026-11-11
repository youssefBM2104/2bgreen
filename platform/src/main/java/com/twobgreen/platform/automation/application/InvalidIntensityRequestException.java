package com.twobgreen.platform.automation.application;

/** A parameter of the public intensity API is missing or wrong. The message is written for the caller. */
public class InvalidIntensityRequestException extends RuntimeException {

	public InvalidIntensityRequestException(String message) {
		super(message);
	}
}
