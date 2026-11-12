package com.twobgreen.platform.regional.application;

/**
 * The caller's token has no {@code region_code}, or one that names no known region: the account of the official was
 * not attached to a region in Keycloak.
 */
public class RegionNotAssignedException extends RuntimeException {

	public RegionNotAssignedException() {
		super("The account is not attached to a known region");
	}
}
