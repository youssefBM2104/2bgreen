package com.twobgreen.platform.delivery.domain;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

/** Storage of {@link WebhookDelivery}. */
public interface WebhookDeliveryRepository extends ListCrudRepository<WebhookDelivery, Long> {

	/** The log of one webhook as its owner sees it, newest first. */
	List<WebhookDelivery> findByWebhookIdAndUserIdOrderByAttemptedAtDesc(long webhookId, String userId);

	/** Retention. */
	@Modifying
	@Query("DELETE FROM delivery_webhook_delivery WHERE attempted_at < :limit")
	int deleteAttemptedBefore(@Param("limit") Instant limit);

	/** Account deletion. */
	@Modifying
	@Query("DELETE FROM delivery_webhook_delivery WHERE user_id = :userId")
	int deleteAllOfUser(@Param("userId") String userId);
}
