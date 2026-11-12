package com.twobgreen.platform.regional.application;

/** The reference year has too little data in the region to compute the reference value of a target. */
public class ReferenceUnavailableException extends RuntimeException {

	public ReferenceUnavailableException(int year) {
		super("Not enough data in " + year + " to compute the reference value");
	}
}
