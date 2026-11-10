package com.twobgreen.platform.automation.domain;

/** What one evaluation of a subject led to. */
public enum CrossingOutcome {

	/** The condition became true: notify, once. */
	FIRED,

	/** The condition stopped being true: the subject can fire again. */
	REARMED,

	/** Same side of the condition as before. */
	UNCHANGED,

	/** The condition is met but notifying is held back (too soon after the last one, or the snapshot is old). */
	HELD_BACK,

	/** This snapshot, or a newer one, was already evaluated: a replay. Nothing is stored. */
	ALREADY_SEEN
}
