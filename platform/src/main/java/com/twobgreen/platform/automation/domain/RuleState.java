package com.twobgreen.platform.automation.domain;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Memory of the rule engine for one rule, webhook or target (P8, U16). Inventory row 12. It is what makes a
 * crossing notify once, and a replayed event notify nothing.
 *
 * @param id null until stored
 * @param userId owner of the subject, copied here so the row can be purged with the account
 * @param subjectId id of the rule, webhook or target: a plain reference, not a foreign key
 * @param state a {@link RuleStatus} name
 * @param lastCrossingAt when the condition last became true, null if never
 * @param lastObservedAt {@code observedAt} of the last snapshot evaluated, null if none
 */
@Table("automation_rule_state")
public record RuleState(@Id Long id, String userId, RuleSubject subjectType, long subjectId, String state,
		Instant lastCrossingAt, Instant lastObservedAt) {
}
