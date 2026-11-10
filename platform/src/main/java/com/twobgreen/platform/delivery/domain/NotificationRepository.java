package com.twobgreen.platform.delivery.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

/** Storage of {@link Notification}. */
public interface NotificationRepository extends ListCrudRepository<Notification, Long> {

	List<Notification> findByUserIdOrderByCreatedAtDesc(String userId);

	Optional<Notification> findByIdAndUserId(Long id, String userId);

	/** The number on the badge. */
	long countByUserIdAndReadAtIsNull(String userId);

	/** Retention. */
	@Modifying
	@Query("DELETE FROM delivery_notification WHERE created_at < :limit")
	int deleteCreatedBefore(@Param("limit") Instant limit);

	/** Account deletion. */
	@Modifying
	@Query("DELETE FROM delivery_notification WHERE user_id = :userId")
	int deleteAllOfUser(@Param("userId") String userId);
}
