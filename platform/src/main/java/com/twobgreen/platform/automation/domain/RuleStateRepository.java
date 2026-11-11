package com.twobgreen.platform.automation.domain;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

/** Storage of {@link RuleState}. */
public interface RuleStateRepository extends ListCrudRepository<RuleState, Long> {

	Optional<RuleState> findBySubjectTypeAndSubjectId(RuleSubject subjectType, long subjectId);

	/**
	 * The state of a subject, locked until the end of the transaction: two evaluations of the same subject cannot
	 * decide from the same state.
	 */
	@Query("""
			SELECT * FROM automation_rule_state
			WHERE subject_type = :subjectType AND subject_id = :subjectId
			FOR UPDATE
			""")
	Optional<RuleState> lockOfSubject(@Param("subjectType") String subjectType, @Param("subjectId") long subjectId);

	/**
	 * Stores the state after the evaluation of a snapshot, unless this snapshot or a newer one was already
	 * evaluated. Returns 1 when the state moved, 0 for a replay: the caller notifies only on 1.
	 */
	@Modifying
	@Query("""
			INSERT INTO automation_rule_state
				(user_id, subject_type, subject_id, state, last_crossing_at, last_observed_at)
			VALUES (:userId, :subjectType, :subjectId, :state, :lastCrossingAt, :observedAt)
			ON CONFLICT (subject_type, subject_id) DO UPDATE
			SET state = EXCLUDED.state,
				last_crossing_at = EXCLUDED.last_crossing_at,
				last_observed_at = EXCLUDED.last_observed_at
			WHERE automation_rule_state.last_observed_at IS NULL
				OR automation_rule_state.last_observed_at < EXCLUDED.last_observed_at
			""")
	int advance(@Param("userId") String userId, @Param("subjectType") String subjectType,
			@Param("subjectId") long subjectId, @Param("state") String state,
			@Param("lastCrossingAt") Instant lastCrossingAt, @Param("observedAt") Instant observedAt);

	/**
	 * To call when the subject is deleted. A target lives in another module, so for {@link RuleSubject#TARGET}
	 * this is the reaction to its deletion event.
	 */
	@Modifying
	@Query("DELETE FROM automation_rule_state WHERE subject_type = :subjectType AND subject_id = :subjectId")
	int deleteOfSubject(@Param("subjectType") String subjectType, @Param("subjectId") long subjectId);

	/** Account deletion. */
	@Modifying
	@Query("DELETE FROM automation_rule_state WHERE user_id = :userId")
	int deleteAllOfUser(@Param("userId") String userId);
}
