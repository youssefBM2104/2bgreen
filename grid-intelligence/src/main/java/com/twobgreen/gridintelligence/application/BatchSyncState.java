package com.twobgreen.gridintelligence.application;

import java.util.List;

/**
 * Port to what the batch remembers between two runs: the counts it saw for each year at the moment it last loaded
 * that year.
 */
public interface BatchSyncState {

	/** The counts of every year already loaded, as recorded. */
	List<YearQualityCount> loaded();

	/**
	 * Records that {@code year} has been loaded while the source showed {@code counts}, replacing what was recorded
	 * for that year (a quality that disappeared from the source disappears here too). Call it only after the
	 * year's readings are stored, so a crash in between makes the next run load the year again.
	 */
	void markLoaded(int year, List<YearQualityCount> counts);
}
