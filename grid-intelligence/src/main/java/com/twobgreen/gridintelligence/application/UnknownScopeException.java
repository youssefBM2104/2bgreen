package com.twobgreen.gridintelligence.application;

/**
 * The region code is not an INSEE region code at all (K2: {@code SCOPE_UNKNOWN}), a bug on the caller side. A
 * well-formed code that has no data yet is not this: it is {@link NoSnapshotException} or an empty range.
 */
public class UnknownScopeException extends RuntimeException {

	private final String code;

	public UnknownScopeException(String code) {
		super("not a region code");
		this.code = code;
	}

	/** The code as the caller wrote it. */
	public String code() {
		return code;
	}
}
