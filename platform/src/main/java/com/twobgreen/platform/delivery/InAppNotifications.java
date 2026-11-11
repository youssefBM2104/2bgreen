package com.twobgreen.platform.delivery;

/**
 * The in-app channel: what a user finds in their notification box. Each call stores one notification, which is also
 * its log. A call joins the transaction of its caller, so the notification exists exactly when the caller's own
 * change does.
 */
public interface InAppNotifications {

	/**
	 * A clean-grid alert rule fired (U2).
	 *
	 * @param userId owner of the rule, the {@code sub} claim of the Keycloak token
	 * @param alertRuleId the rule that fired, kept as a plain reference
	 * @param message short text shown to the user, 1 to 500 characters
	 */
	void cleanGrid(String userId, long alertRuleId, String message);
}
