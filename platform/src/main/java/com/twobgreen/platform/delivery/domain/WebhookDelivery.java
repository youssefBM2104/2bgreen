package com.twobgreen.platform.delivery.domain;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * One attempt to call a webhook (U9). Inventory row 9. Neither the payload sent nor the body received is kept.
 *
 * @param id null until stored
 * @param deliveryId sent as {@code X-2bGreen-Delivery}; the attempts of one delivery share it
 * @param userId owner of the webhook, copied here because the log outlives the webhook
 * @param webhookId a plain reference to module {@code automation}; the webhook may be gone
 * @param attempt 1 for the first call, then one more per retry
 * @param httpStatus null when there was no HTTP answer
 * @param errorKind short reason when there was no HTTP answer (timeout, refused...), else null
 */
@Table("delivery_webhook_delivery")
public record WebhookDelivery(@Id Long id, UUID deliveryId, String userId, long webhookId, int attempt,
		Instant attemptedAt, DeliveryOutcome outcome, Integer httpStatus, String errorKind) {
}
