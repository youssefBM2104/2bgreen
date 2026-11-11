package com.twobgreen.platform.regional.domain;

/** Whether a scope produces less or more than it consumes at a reading. */
public enum BalanceDirection {

	/** Produces less than it consumes: the rest comes from other regions or abroad. */
	IMPORTING,

	/** Produces more than it consumes: the surplus goes to other regions or abroad. */
	EXPORTING,

	/** Produces exactly what it consumes. */
	BALANCED
}
