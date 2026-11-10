package com.twobgreen.gridintelligence.application;

import com.twobgreen.gridintelligence.domain.Quality;

/**
 * How many rows the source holds for one UTC year at one quality. The batch compares these counts between two runs
 * to know which years to download again, instead of downloading everything every day.
 * <p>
 * A year can have two entries: quality flips on Paris years while counts are by UTC year, so the last hour of a UTC
 * year can still belong to the next Paris year (2025 holds 4 consolidated rows, the first hour of 2026 in Paris).
 *
 * @param rows rows as the source counts them, empty placeholders included: the figure is only ever compared with
 *        the same figure from an earlier run, never with a number of snapshots
 */
public record YearQualityCount(int year, Quality quality, long rows) {

	public YearQualityCount {
		if (quality == null || quality == Quality.PROVISIONAL) {
			throw new IllegalArgumentException("a batch count is CONSOLIDATED or DEFINITIVE");
		}
		if (rows < 0) {
			throw new IllegalArgumentException("rows must not be negative");
		}
	}
}
