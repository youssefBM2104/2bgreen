package com.twobgreen.platform.automation.domain;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * A URL to call when a condition becomes true (U9). Inventory row 8: the DevOps subscription.
 * <p>
 * {@code url} and {@code secret} are credentials: never log them, and never return the secret after creation.
 * The secret is stored as is because signing a call (HMAC-SHA256) needs it.
 *
 * @param id null until stored
 * @param userId the {@code sub} claim of the Keycloak token
 * @param threshold gCO2eq/kWh, null exactly when the condition is {@link WebhookCondition#BAND_CHANGED}
 * @param consecutiveFailures failed deliveries in a row, back to 0 on a success
 */
@Table("automation_webhook")
public record Webhook(@Id Long id, String userId, String url, String scope, WebhookCondition conditionType,
		Integer threshold, String secret, boolean active, int consecutiveFailures, Instant createdAt) {

	/** Without the URL and the secret, so a log line or an error message cannot leak them. */
	@Override
	public String toString() {
		return "Webhook[id=" + id + ", conditionType=" + conditionType + ", active=" + active + "]";
	}
}
