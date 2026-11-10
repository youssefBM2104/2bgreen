package com.twobgreen.platform.delivery;

import java.util.UUID;

/**
 * One call to make to the URL of a webhook (U9).
 *
 * @param deliveryId identity of the delivery, sent to the receiver so it can deduplicate; the same crossing always
 *        gives the same id
 * @param userId owner of the webhook
 * @param webhookId a plain reference to the webhook
 * @param url where to send; a credential, never logged
 * @param secret signs the body; a credential, never logged
 * @param body JSON to send; never stored
 */
public record WebhookCall(UUID deliveryId, String userId, long webhookId, String url, String secret, String body) {

	/** Without the URL, the secret and the body, so a log line or an error message cannot leak them. */
	@Override
	public String toString() {
		return "WebhookCall[deliveryId=" + deliveryId + ", webhookId=" + webhookId + "]";
	}
}
