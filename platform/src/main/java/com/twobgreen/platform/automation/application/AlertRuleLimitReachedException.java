package com.twobgreen.platform.automation.application;

/** The user already has {@link ManageAlertRules#MAX_RULES_PER_USER} rules. */
public class AlertRuleLimitReachedException extends RuntimeException {

	public AlertRuleLimitReachedException() {
		super("Limit of " + ManageAlertRules.MAX_RULES_PER_USER + " alert rules reached");
	}
}
