package com.twobgreen.platform.automation.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

/** Storage of {@link ApiKey} and of the daily call counters that follow each key. */
public interface ApiKeyRepository extends ListCrudRepository<ApiKey, Long> {

	List<ApiKey> findByUserIdOrderById(String userId);

	/** The keys a user can still use, oldest first. */
	List<ApiKey> findByUserIdAndRevokedAtIsNullOrderById(String userId);

	Optional<ApiKey> findByIdAndUserId(Long id, String userId);

	/** The lookup of the authentication filter. A revoked key is still found: the caller checks {@code revokedAt}. */
	Optional<ApiKey> findByKeyHash(String keyHash);

	/** For the limit of active keys per user. */
	long countByUserIdAndRevokedAtIsNull(String userId);

	/**
	 * Makes the transactions that create a key for one user run one after the other, until the end of the caller's
	 * transaction. Without it two of them could both count one key less than the limit. Returns 1.
	 */
	@Query("""
			SELECT count(*)
			FROM (SELECT pg_advisory_xact_lock(hashtext('automation_api_key'), hashtext(:userId))) AS locked
			""")
	long lockKeysOfUser(@Param("userId") String userId);

	/**
	 * Counts one call of a key on a UTC day (inventory row 7) if the key has made fewer than {@code limit} calls that
	 * day, and returns the new total. Empty when the limit is already reached: a refused call is not counted. One
	 * atomic statement, so concurrent calls cannot lose an increment nor pass the limit together (PostgreSQL locks the
	 * row and checks the condition again on its latest version).
	 */
	@Query("""
			INSERT INTO automation_api_key_daily_counter (api_key_id, day, call_count)
			VALUES (:apiKeyId, :day, 1)
			ON CONFLICT (api_key_id, day) DO UPDATE
			SET call_count = automation_api_key_daily_counter.call_count + 1
			WHERE automation_api_key_daily_counter.call_count < :limit
			RETURNING call_count
			""")
	Optional<Integer> countCall(@Param("apiKeyId") Long apiKeyId, @Param("day") LocalDate day,
			@Param("limit") int limit);

	/** Records when a key was last accepted. */
	@Modifying
	@Query("UPDATE automation_api_key SET last_used_at = :at WHERE id = :id")
	int markUsed(@Param("id") Long id, @Param("at") Instant at);

	/** Calls of a key on a UTC day, empty when there were none. */
	@Query("SELECT call_count FROM automation_api_key_daily_counter WHERE api_key_id = :apiKeyId AND day = :day")
	Optional<Integer> callsOn(@Param("apiKeyId") Long apiKeyId, @Param("day") LocalDate day);

	/** Retention of the counters. */
	@Modifying
	@Query("DELETE FROM automation_api_key_daily_counter WHERE day < :limit")
	int deleteCountersBefore(@Param("limit") LocalDate limit);

	/** Retention of revoked keys. Their counters go with them. */
	@Modifying
	@Query("DELETE FROM automation_api_key WHERE revoked_at < :limit")
	int deleteRevokedBefore(@Param("limit") Instant limit);

	/** Account deletion. The counters go with the keys. */
	@Modifying
	@Query("DELETE FROM automation_api_key WHERE user_id = :userId")
	int deleteAllOfUser(@Param("userId") String userId);
}
