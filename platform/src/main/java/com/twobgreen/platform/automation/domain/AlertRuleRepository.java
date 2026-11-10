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

	/** What the rule engine evaluates when a snapshot of that scope arrives. */
	List<AlertRule> findByScopeAndActiveTrue(String scope);

	/** Account deletion. */
	@Modifying
	@Query("DELETE FROM automation_alert_rule WHERE user_id = :userId")
	int deleteAllOfUser(@Param("userId") String userId);
}
