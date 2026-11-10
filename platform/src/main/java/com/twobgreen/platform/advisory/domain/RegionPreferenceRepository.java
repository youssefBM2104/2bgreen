package com.twobgreen.platform.advisory.domain;

import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

/** Storage of {@link RegionPreference}. */
public interface RegionPreferenceRepository extends ListCrudRepository<RegionPreference, Long> {

	Optional<RegionPreference> findByUserId(String userId);

	/** The user's own "forget my region", and account deletion. */
	@Modifying
	@Query("DELETE FROM advisory_region_preference WHERE user_id = :userId")
	int deleteAllOfUser(@Param("userId") String userId);
}
