package com.twobgreen.platform.advisory.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

/** Storage of {@link Usage}. Every read takes the user id: a user only reaches their own rows. */
public interface UsageRepository extends ListCrudRepository<Usage, Long> {

	List<Usage> findByUserIdOrderByLabel(String userId);

	/** Empty when the usage is someone else's: a user never reaches another user's rows. */
	Optional<Usage> findByIdAndUserId(Long id, String userId);

	/** Account deletion. */
	@Modifying
	@Query("DELETE FROM advisory_usage WHERE user_id = :userId")
	int deleteAllOfUser(@Param("userId") String userId);
}
