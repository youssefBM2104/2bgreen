package com.twobgreen.platform.automation.infrastructure;

import java.time.Instant;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import com.twobgreen.platform.automation.application.ManageAlertRules;
import com.twobgreen.platform.automation.domain.AlertRule;

/**
 * Bodies of {@code /api/v1/automation/alerts}. The user id and the scope are not part of them: the first comes from
 * the token, the second is always national.
 */
final class AlertRuleJson {

	private AlertRuleJson() {
	}

	/**
	 * @param threshold gCO2eq/kWh
	 * @param active optional, a new rule is active unless told otherwise
	 */
	record Create(
			@NotNull @Min(ManageAlertRules.MIN_THRESHOLD) @Max(ManageAlertRules.MAX_THRESHOLD) Integer threshold,
			Boolean active) {
	}

	/** The whole rule as the user wants it: both fields are required. */
	record Replace(
			@NotNull @Min(ManageAlertRules.MIN_THRESHOLD) @Max(ManageAlertRules.MAX_THRESHOLD) Integer threshold,
			@NotNull Boolean active) {
	}

	record View(long id, int threshold, boolean active, Instant createdAt, Instant updatedAt) {

		static View of(AlertRule rule) {
			return new View(rule.id(), rule.threshold(), rule.active(), rule.createdAt(), rule.updatedAt());
		}
	}
}
