package com.twobgreen.platform.delivery.domain;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * A notification shown in the application (U2, U16). Inventory row 2.
 *
 * @param id null until stored
 * @param userId the {@code sub} claim of the Keycloak token
 * @param sourceId id of the alert rule or of the target that caused it: a plain reference to another module
 * @param message short text shown to the user
 * @param readAt null while unread
 */
@Table("delivery_notification")
public record Notification(@Id Long id, String userId, NotificationType type, long sourceId, String message,
		Instant createdAt, Instant readAt) {
}
