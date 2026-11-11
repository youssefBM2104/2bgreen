package com.twobgreen.platform.automation.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

/** Storage of {@link AlertRule}. */
public interface AlertRuleRepository extends ListCrudRepository<AlertRule, Long> {

	List<AlertRule> findByUserIdOrderById(String userId);

	Optional<AlertRule> findByIdAndUserId(Long id, String userId);

	/** For the limit of rules per user. */
	long countByUserId(String userId);

	/**
	 * Makes the transactions that create a rule for one user run one after the other, until the end of the caller's
	 * transaction. Without it two of them could both count one rule less than the limit. Returns 1.
	 */
	@Query("""
			SELECT count(*)
			FROM (SELECT pg_advisory_xact_lock(hashtext('automation_alert_rule'), hashtext(:userId))) AS locked
			""")
	long lockRulesOfUser(@Param("userId") String userId);

	/** What the rule engine evaluates when a snapshot of that scope arrives. */
	List<AlertRule> findByScopeAndActiveTrue(String scope);

	/** Account deletion. */
	@Modifying
	@Query("DELETE FROM automation_alert_rule WHERE user_id = :userId")
	int deleteAllOfUser(@Param("userId") String userId);
}
