package com.twobgreen.gridintelligence.application;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.twobgreen.gridintelligence.domain.GridSnapshot;

/**
 * Use case of the batch layer: bring {@code batch_snapshot} up to date with the consolidated and definitive national
 * history of ODRÉ, at the lowest possible cost in calls.
 * <p>
 * One cheap call tells how many rows ODRÉ holds per UTC year and quality. That is compared with what was recorded
 * when each year was last loaded, and only the years that differ are downloaded again. A year differs when rows are
 * added to it, or when its rows move from consolidated to definitive (the counts per quality change). On a normal
 * day nothing differs and the whole run costs one call.
 * <p>
 * Nothing needs a transaction, because every step can be repeated:
 * <ol>
 * <li>the counts are read <em>before</em> the download, so a change made by ODRÉ while a year downloads shows up as a
 * difference at the next run instead of being recorded as loaded;</li>
 * <li>a year is recorded as loaded only <em>after</em> its readings are stored, so a crash in between makes the next
 * run load it again, and the idempotent upsert absorbs the repetition.</li>
 * </ol>
 */
public class SyncNationalConsolidated {

	/**
	 * What one run did, for the logs.
	 *
	 * @param yearsChecked years ODRÉ holds data for
	 * @param yearsLoaded years downloaded again, oldest first
	 * @param rowsWritten rows inserted or updated in the table (0 when nothing changed)
	 */
	public record Result(int yearsChecked, List<Integer> yearsLoaded, int rowsWritten) {
	}

	private final ConsolidatedOdreGateway odre;

	private final BatchSnapshotRepository snapshots;

	private final BatchSyncState state;

	public SyncNationalConsolidated(ConsolidatedOdreGateway odre, BatchSnapshotRepository snapshots,
			BatchSyncState state) {
		this.odre = odre;
		this.snapshots = snapshots;
		this.state = state;
	}

	/**
	 * One run, oldest year first. On failure the exception goes to the caller: the years already loaded stay recorded,
	 * so the next run resumes with the year that failed.
	 *
	 * @throws OdreException if ODRÉ cannot be read
	 * @throws RuntimeException if the readings cannot be stored
	 */
	public synchronized Result sync() {
		Map<Integer, Set<YearQualityCount>> current = byYear(odre.nationalCounts());
		Map<Integer, Set<YearQualityCount>> loaded = byYear(state.loaded());
		List<Integer> yearsLoaded = new ArrayList<>();
		int rowsWritten = 0;
		for (Map.Entry<Integer, Set<YearQualityCount>> year : current.entrySet()) {
			if (year.getValue().equals(loaded.get(year.getKey()))) {
				continue;
			}
			List<GridSnapshot> readings = odre.nationalReadings(year.getKey());
			rowsWritten += snapshots.upsert(readings);
			state.markLoaded(year.getKey(), List.copyOf(year.getValue()));
			yearsLoaded.add(year.getKey());
		}
		return new Result(current.size(), yearsLoaded, rowsWritten);
	}

	/** Years in ascending order; the counts of one year as a set, since only their content matters. */
	private static Map<Integer, Set<YearQualityCount>> byYear(List<YearQualityCount> counts) {
		return counts.stream().collect(Collectors.groupingBy(YearQualityCount::year, TreeMap::new,
				Collectors.toCollection(HashSet::new)));
	}
}
