package com.twobgreen.gridintelligence.application;

/**
 * Nothing with measures is stored yet for the scope. Normal on a fresh start, not a failure (K2: {@code NO_DATA}).
 */
public class NoSnapshotException extends RuntimeException {

	public NoSnapshotException(String scope) {
		super("no snapshot with measures for scope " + scope);
	}
}
