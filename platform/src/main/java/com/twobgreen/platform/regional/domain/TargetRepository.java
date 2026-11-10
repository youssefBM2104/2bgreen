package com.twobgreen.platform.regional.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

/** Storage of {@link Target}. */
public interface TargetRepository extends ListCrudRepository<Target, Long> {

	List<Target> findByUserIdOrderById(String userId);

	Optional<Target> findByIdAndUserId(Long id, String userId);

	/** Account deletion. The progress history goes with the targets. */
	@Modifying
	@Query("DELETE FROM regional_target WHERE user_id = :userId")
	int deleteAllOfUser(@Param("userId") String userId);
}
