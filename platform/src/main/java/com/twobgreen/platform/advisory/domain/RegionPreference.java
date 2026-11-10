package com.twobgreen.platform.advisory.domain;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * The region a citizen picked from the list (U3, still a proposal). Inventory row 5. One row per user at most.
 *
 * @param id null until stored
 * @param userId the {@code sub} claim of the Keycloak token
 * @param regionCode INSEE code of the region, two digits; never a location
 */
@Table("advisory_region_preference")
public record RegionPreference(@Id Long id, String userId, String regionCode, Instant updatedAt) {
}
