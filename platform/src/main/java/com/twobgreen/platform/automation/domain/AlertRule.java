package com.twobgreen.platform.automation.domain;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Notifies a citizen when CO2 goes below their limit (U2). Inventory row 1: the citizen's subscription is this rule.
 *
 * @param id null until stored
 * @param userId the {@code sub} claim of the Keycloak token
 * @param scope {@code national} for now
 * @param threshold gCO2eq/kWh, 1 to 1000
 */
@Table("automation_alert_rule")
public record AlertRule(@Id Long id, String userId, String scope, int threshold, boolean active, Instant createdAt,
		Instant updatedAt) {
}
