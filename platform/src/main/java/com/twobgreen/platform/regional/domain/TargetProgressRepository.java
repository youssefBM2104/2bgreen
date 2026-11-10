package com.twobgreen.platform.regional.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

/** Storage of {@link TargetProgress}. */
public interface TargetProgressRepository extends ListCrudRepository<TargetProgress, Long> {

	List<TargetProgress> findByTargetIdOrderByDay(long targetId);

	/**
	 * Records the progress of a day. Running the daily recalculation twice replaces the row instead of adding one.
	 */
	@Modifying
	@Query("""
			INSERT INTO regional_target_progress (target_id, day, value, status)
			VALUES (:targetId, :day, :value, :status)
			ON CONFLICT (target_id, day) DO UPDATE SET value = EXCLUDED.value, status = EXCLUDED.status
			""")
	int record(@Param("targetId") long targetId, @Param("day") LocalDate day, @Param("value") BigDecimal value,
			@Param("status") String status);
}
