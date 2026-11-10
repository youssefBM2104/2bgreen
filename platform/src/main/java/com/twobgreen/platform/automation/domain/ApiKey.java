package com.twobgreen.platform.automation.domain;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * An API key of a DevOps user (U10). Inventory row 6. The key itself is shown once and never stored.
 *
 * @param id null until stored
 * @param userId the {@code sub} claim of the Keycloak token
 * @param prefix start of the key, shown in the list
 * @param keyHash SHA-256 of the key, 64 lowercase hex characters
 * @param revokedAt null while the key is valid
 */
@Table("automation_api_key")
public record ApiKey(@Id Long id, String userId, String name, String prefix, String keyHash, Instant createdAt,
		Instant revokedAt, Instant lastUsedAt) {
}
