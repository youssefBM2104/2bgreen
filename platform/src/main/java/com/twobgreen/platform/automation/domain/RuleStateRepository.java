package com.twobgreen.platform.automation.domain;

import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

/** Storage of {@link RuleState}. */
public interface RuleStateRepository extends ListCrudRepository<RuleState, Long> {

	Optional<RuleState> findBySubjectTypeAndSubjectId(RuleSubject subjectType, long subjectId);

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
