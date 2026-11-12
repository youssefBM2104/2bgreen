package com.twobgreen.gridintelligence.infrastructure.persistence;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import com.twobgreen.gridintelligence.application.BatchSyncState;
import com.twobgreen.gridintelligence.application.YearQualityCount;
import com.twobgreen.gridintelligence.domain.Quality;

/**
 * {@link BatchSyncState} of one dataset, stored under its key in column {@code scope}: {@code national} for the
 * national cons-def (G3), {@link #REGIONAL} for the regional one (G6), whose counts cover every region at once.
 */
class JdbcBatchSyncState implements BatchSyncState {

	/** Key of the regional cons-def state: not a scope of the snapshots, the counts are for all regions together. */
	static final String REGIONAL = "regional";

	private final JdbcClient jdbc;

	private final String key;

	JdbcBatchSyncState(JdbcClient jdbc, String key) {
		this.jdbc = jdbc;
		this.key = key;
	}

	@Override
	public List<YearQualityCount> loaded() {
		return jdbc.sql("SELECT year, quality, row_count FROM batch_sync_state WHERE scope = :scope ORDER BY year, quality")
				.param("scope", key)
				.query((rs, i) -> new YearQualityCount(rs.getInt("year"), Quality.valueOf(rs.getString("quality")),
						rs.getLong("row_count")))
				.list();
	}

	// Delete then insert in one transaction: a quality that left the source must not linger in the state
	@Override
	@Transactional
	public void markLoaded(int year, List<YearQualityCount> counts) {
		jdbc.sql("DELETE FROM batch_sync_state WHERE scope = :scope AND year = :year")
				.param("scope", key)
				.param("year", year)
				.update();
		for (YearQualityCount count : counts) {
			if (count.year() != year) {
				throw new IllegalArgumentException("count of " + count.year() + " given for " + year);
			}
			jdbc.sql("INSERT INTO batch_sync_state (scope, year, quality, row_count) "
					+ "VALUES (:scope, :year, :quality, :rows)")
					.param("scope", key)
					.param("year", year)
					.param("quality", count.quality().name())
					.param("rows", count.rows())
					.update();
		}
	}
}
