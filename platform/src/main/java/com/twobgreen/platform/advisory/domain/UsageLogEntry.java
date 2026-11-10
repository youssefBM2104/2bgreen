package com.twobgreen.platform.advisory.domain;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Something a citizen actually ran (U6). Inventory row 4. It holds no CO2 value: the intensity is computed when
 * the log is read, so consolidated data replaces provisional data on its own.
 *
 * @param id null until stored
 * @param userId the {@code sub} claim of the Keycloak token
 * @param usageLabel a copy of the label, so the entry survives the deletion of the usage
 * @param startedAt UTC
 */
@Table("advisory_usage_log_entry")
public record UsageLogEntry(@Id Long id, String userId, String usageLabel, Instant startedAt, int durationMinutes,
		Instant createdAt) {
}
