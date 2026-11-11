package com.twobgreen.platform.automation.application;

/** No such rule for this user. A rule of somebody else is answered the same way, so its existence is not revealed. */
public class AlertRuleNotFoundException extends RuntimeException {

	public AlertRuleNotFoundException(long id) {
		super("No alert rule " + id + " for this user");
	}
}
