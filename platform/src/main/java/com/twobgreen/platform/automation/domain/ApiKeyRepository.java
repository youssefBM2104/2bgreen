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

	Optional<ApiKey> findByIdAndUserId(Long id, String userId);

	/** The lookup of the authentication filter. A revoked key is still found: the caller checks {@code revokedAt}. */
	Optional<ApiKey> findByKeyHash(String keyHash);

	/** For the limit of active keys per user. */
	long countByUserIdAndRevokedAtIsNull(String userId);

	/**
	 * Counts one call of a key on a UTC day (inventory row 7) and returns the new total. One atomic statement, so
	 * concurrent calls cannot lose an increment.
	 */
	@Query("""
			INSERT INTO automation_api_key_daily_counter (api_key_id, day, call_count)
			VALUES (:apiKeyId, :day, 1)
			ON CONFLICT (api_key_id, day) DO UPDATE
			SET call_count = automation_api_key_daily_counter.call_count + 1
			RETURNING call_count
			""")
	int countCall(@Param("apiKeyId") Long apiKeyId, @Param("day") LocalDate day);

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
