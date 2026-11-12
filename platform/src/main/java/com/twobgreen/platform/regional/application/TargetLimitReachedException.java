package com.twobgreen.platform.regional.application;

/** The official already has {@link ManageTargets#MAX_TARGETS_PER_USER} targets. */
public class TargetLimitReachedException extends RuntimeException {

	public TargetLimitReachedException() {
		super("Target limit reached");
	}
}
