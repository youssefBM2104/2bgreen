package com.twobgreen.platform.automation.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

/** Storage of {@link Webhook}. */
public interface WebhookRepository extends ListCrudRepository<Webhook, Long> {

	List<Webhook> findByUserIdOrderById(String userId);

	Optional<Webhook> findByIdAndUserId(Long id, String userId);

	/** For the limit of webhooks per user. */
	long countByUserId(String userId);

	/** What the rule engine evaluates when a snapshot of that scope arrives. */
	List<Webhook> findByScopeAndActiveTrue(String scope);

	/** Account deletion. */
	@Modifying
	@Query("DELETE FROM automation_webhook WHERE user_id = :userId")
	int deleteAllOfUser(@Param("userId") String userId);
}
