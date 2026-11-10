package com.twobgreen.platform.regional.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * A target a regional official set for their region (U13). Inventory row 10.
 *
 * @param id null until stored
 * @param userId the author, the {@code sub} claim of the Keycloak token
 * @param regionCode INSEE code of the region, the one of the author's token
 * @param referenceValue value of the indicator for the reference year, computed once at creation
 * @param tolerance allowed gap to the trajectory in points; null turns the pace alert (U16) off
 */
@Table("regional_target")
public record Target(@Id Long id, String userId, String regionCode, TargetIndicator indicator, int referenceYear,
		BigDecimal referenceValue, BigDecimal targetValue, LocalDate deadline, BigDecimal tolerance,
		Instant createdAt) {
}
