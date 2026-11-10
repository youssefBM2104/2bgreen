package com.twobgreen.platform.advisory.domain;

import java.time.Instant;
import java.time.LocalTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * A usage a citizen wants a best slot for (U4), such as a dishwasher run of 3 hours. Inventory row 3.
 *
 * @param id null until stored
 * @param userId the {@code sub} claim of the Keycloak token
 * @param finishBefore Paris local time of day the usage must be done by, or null
 */
@Table("advisory_usage")
public record Usage(@Id Long id, String userId, String label, int durationMinutes, LocalTime finishBefore,
		Instant createdAt) {
}
