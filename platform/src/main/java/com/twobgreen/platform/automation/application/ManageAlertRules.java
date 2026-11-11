package com.twobgreen.platform.automation.application;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.transaction.support.TransactionOperations;

import com.twobgreen.platform.automation.domain.AlertRule;
import com.twobgreen.platform.automation.domain.AlertRuleRepository;
import com.twobgreen.platform.automation.domain.RuleStateRepository;
import com.twobgreen.platform.automation.domain.RuleSubject;

/**
 * Use case of the citizen space (U2): a user manages their own clean-grid alert rules. Every method takes the user
 * id of the caller and only ever reaches the rules of that user.
 * <p>
 * The rule engine keeps a memory per rule ({@code automation_rule_state}). It describes the rule as it was, so it is
 * dropped in the same transaction whenever the limit or the active flag changes, and when the rule is deleted. A rule
 * that is changed or switched on again is then compared with the next snapshot as a new rule would be.
 */
public class ManageAlertRules {

	public static final int MAX_RULES_PER_USER = 5;

	public static final int MIN_THRESHOLD = 1;

	public static final int MAX_THRESHOLD = 1000;

	/** The only scope a citizen can follow for now. */
	static final String NATIONAL = "national";

	private final AlertRuleRepository alertRules;

	private final RuleStateRepository states;

	private final TransactionOperations transaction;

	private final Clock clock;

	public ManageAlertRules(AlertRuleRepository alertRules, RuleStateRepository states,
			TransactionOperations transaction, Clock clock) {
		this.alertRules = alertRules;
		this.states = states;
		this.transaction = transaction;
		this.clock = clock;
	}

	public List<AlertRule> listOf(String userId) {
		return alertRules.findByUserIdOrderById(userId);
	}

	/**
	 * @throws AlertRuleLimitReachedException if the user already has {@link #MAX_RULES_PER_USER} rules
	 */
	public AlertRule create(String userId, int threshold, boolean active) {
		requireValid(threshold);
		return transaction.execute(status -> {
			// Two requests of one user wait for each other here, so both cannot count 4 and both insert
			alertRules.lockRulesOfUser(userId);
			if (alertRules.countByUserId(userId) >= MAX_RULES_PER_USER) {
				throw new AlertRuleLimitReachedException();
			}
			Instant now = now();
			return alertRules.save(new AlertRule(null, userId, NATIONAL, threshold, active, now, now));
		});
	}

	/**
	 * @throws AlertRuleNotFoundException if the user has no such rule
	 */
	public AlertRule update(String userId, long id, int threshold, boolean active) {
		requireValid(threshold);
		return transaction.execute(status -> {
			AlertRule rule = ownRule(userId, id);
			if (rule.threshold() == threshold && rule.active() == active) {
				return rule;
			}
			AlertRule changed = alertRules.save(new AlertRule(rule.id(), rule.userId(), rule.scope(), threshold,
					active, rule.createdAt(), now()));
			states.deleteOfSubject(RuleSubject.ALERT_RULE.name(), id);
			return changed;
		});
	}

	/**
	 * @throws AlertRuleNotFoundException if the user has no such rule
	 */
	public void delete(String userId, long id) {
		transaction.executeWithoutResult(status -> {
			ownRule(userId, id);
			alertRules.deleteById(id);
			states.deleteOfSubject(RuleSubject.ALERT_RULE.name(), id);
		});
	}

	private AlertRule ownRule(String userId, long id) {
		return alertRules.findByIdAndUserId(id, userId).orElseThrow(() -> new AlertRuleNotFoundException(id));
	}

	/** The database keeps microseconds: the rule returned is the rule a later read gives. */
	private Instant now() {
		return clock.instant().truncatedTo(ChronoUnit.MICROS);
	}

	private static void requireValid(int threshold) {
		if (threshold < MIN_THRESHOLD || threshold > MAX_THRESHOLD) {
			throw new IllegalArgumentException("threshold must be between " + MIN_THRESHOLD + " and " + MAX_THRESHOLD);
		}
	}
}
