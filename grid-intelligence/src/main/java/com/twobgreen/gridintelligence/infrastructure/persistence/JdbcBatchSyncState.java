package com.twobgreen.gridintelligence.infrastructure.persistence;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.twobgreen.gridintelligence.application.BatchSyncState;
import com.twobgreen.gridintelligence.application.YearQualityCount;
import com.twobgreen.gridintelligence.domain.GridSnapshot;
import com.twobgreen.gridintelligence.domain.Quality;

@Repository
class JdbcBatchSyncState implements BatchSyncState {

	private final JdbcClient jdbc;

	JdbcBatchSyncState(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<YearQualityCount> loaded() {
		return jdbc.sql("SELECT year, quality, row_count FROM batch_sync_state WHERE scope = :scope ORDER BY year, quality")
				.param("scope", GridSnapshot.NATIONAL_SCOPE)
				.query((rs, i) -> new YearQualityCount(rs.getInt("year"), Quality.valueOf(rs.getString("quality")),
						rs.getLong("row_count")))
				.list();
	}

	// Delete then insert in one transaction: a quality that left the source must not linger in the state
	@Override
	@Transactional
	public void markLoaded(int year, List<YearQualityCount> counts) {
		jdbc.sql("DELETE FROM batch_sync_state WHERE scope = :scope AND year = :year")
				.param("scope", GridSnapshot.NATIONAL_SCOPE)
				.param("year", year)
				.update();
		for (YearQualityCount count : counts) {
			if (count.year() != year) {
				throw new IllegalArgumentException("count of " + count.year() + " given for " + year);
			}
			jdbc.sql("INSERT INTO batch_sync_state (scope, year, quality, row_count) "
					+ "VALUES (:scope, :year, :quality, :rows)")
					.param("scope", GridSnapshot.NATIONAL_SCOPE)
					.param("year", year)
					.param("quality", count.quality().name())
					.param("rows", count.rows())
					.update();
		}
	}
}
