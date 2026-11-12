package com.twobgreen.platform.regional.application;

/** No such target for this official in this region; the same answer when it belongs to someone else. */
public class TargetNotFoundException extends RuntimeException {

	public TargetNotFoundException() {
		super("No such target");
	}
}
