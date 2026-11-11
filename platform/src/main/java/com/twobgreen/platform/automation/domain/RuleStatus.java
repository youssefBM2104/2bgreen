package com.twobgreen.platform.automation.domain;

/** The value kept in {@link RuleState#state()}. */
public enum RuleStatus {

	/** The next snapshot that meets the condition notifies. Also the status of a subject never evaluated. */
	ARMED,

	/** The condition is met and its owner was told. Nothing more is sent until the condition stops being met. */
	FIRED
}
