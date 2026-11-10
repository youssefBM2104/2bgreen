package com.twobgreen.platform.automation.domain;

/** What a {@link RuleState} follows. */
public enum RuleSubject {

	ALERT_RULE,

	WEBHOOK,

	/** A target of module {@code regional}, known here by its id only. */
	TARGET
}
