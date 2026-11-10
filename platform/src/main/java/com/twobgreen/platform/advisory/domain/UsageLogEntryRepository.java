package com.twobgreen.platform.advisory.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

/** Storage of {@link UsageLogEntry}. */
public interface UsageLogEntryRepository extends ListCrudRepository<UsageLogEntry, Long> {

	/** Entries started from {@code from} included to {@code to} excluded, oldest first. */
	@Query("""
			SELECT * FROM advisory_usage_log_entry
			WHERE user_id = :userId AND started_at >= :from AND started_at < :to
			ORDER BY started_at
			""")
	List<UsageLogEntry> findOfUserBetween(@Param("userId") String userId, @Param("from") Instant from,
			@Param("to") Instant to);

	Optional<UsageLogEntry> findByIdAndUserId(Long id, String userId);

	/** Retention: entries started before the limit. */
	@Modifying
	@Query("DELETE FROM advisory_usage_log_entry WHERE started_at < :limit")
	int deleteStartedBefore(@Param("limit") Instant limit);

	/** Account deletion. */
	@Modifying
	@Query("DELETE FROM advisory_usage_log_entry WHERE user_id = :userId")
	int deleteAllOfUser(@Param("userId") String userId);
}
