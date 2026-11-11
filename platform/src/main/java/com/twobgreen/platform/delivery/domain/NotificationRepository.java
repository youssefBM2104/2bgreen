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

	/** What the notification box shows: the newest first, and no more than {@code limit}. */
	@Query("""
			SELECT * FROM delivery_notification
			WHERE user_id = :userId
			ORDER BY created_at DESC, id DESC
			LIMIT :limit
			""")
	List<Notification> findLatestOfUser(@Param("userId") String userId, @Param("limit") int limit);

	/** The number on the badge. */
	long countByUserIdAndReadAtIsNull(String userId);

	/**
	 * Marks one notification of this user as read. A notification already read keeps its first date. Returns the
	 * number of rows changed.
	 */
	@Modifying
	@Query("""
			UPDATE delivery_notification SET read_at = :readAt
			WHERE id = :id AND user_id = :userId AND read_at IS NULL
			""")
	int markRead(@Param("id") long id, @Param("userId") String userId, @Param("readAt") Instant readAt);

	/** Marks every unread notification of this user as read. Returns the number of rows changed. */
	@Modifying
	@Query("UPDATE delivery_notification SET read_at = :readAt WHERE user_id = :userId AND read_at IS NULL")
	int markAllRead(@Param("userId") String userId, @Param("readAt") Instant readAt);

	/** Retention. */
	@Modifying
	@Query("DELETE FROM delivery_notification WHERE created_at < :limit")
	int deleteCreatedBefore(@Param("limit") Instant limit);

	/** Account deletion. */
	@Modifying
	@Query("DELETE FROM delivery_notification WHERE user_id = :userId")
	int deleteAllOfUser(@Param("userId") String userId);
}
