package com.twobgreen.gridintelligence.application;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.IntFunction;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import com.twobgreen.gridintelligence.domain.GridSnapshot;

/**
 * The algorithm shared by the national (G3) and regional (G6) batches: compare what ODRÉ holds per UTC year and quality
 * with what was recorded at the last load, and download again only the years that differ. See
 * {@link SyncNationalConsolidated} for why no step needs a transaction.
 */
final class ConsolidatedYears {

	/**
	 * @param yearsChecked years ODRÉ holds data for, from {@code firstYear}
	 * @param yearsLoaded years downloaded again, oldest first
	 * @param rowsWritten rows inserted or updated in the table (0 when nothing changed)
	 */
	record Outcome(int yearsChecked, List<Integer> yearsLoaded, int rowsWritten) {
	}

	private ConsolidatedYears() {
	}

	/**
	 * @param counts what ODRÉ holds now, read <em>before</em> any download
	 * @param firstYear years before it are ignored
	 * @param enrich applied to each reading before it is stored
	 */
	static Outcome sync(List<YearQualityCount> counts, IntFunction<List<GridSnapshot>> readings,
			BatchSnapshotRepository snapshots, BatchSyncState state, int firstYear, UnaryOperator<GridSnapshot> enrich) {
		Map<Integer, Set<YearQualityCount>> current = byYear(counts, firstYear);
		Map<Integer, Set<YearQualityCount>> loaded = byYear(state.loaded(), firstYear);
		List<Integer> yearsLoaded = new ArrayList<>();
		int rowsWritten = 0;
		for (Map.Entry<Integer, Set<YearQualityCount>> year : current.entrySet()) {
			if (year.getValue().equals(loaded.get(year.getKey()))) {
				continue;
			}
			List<GridSnapshot> rows = readings.apply(year.getKey()).stream().map(enrich).toList();
			rowsWritten += snapshots.upsert(rows);
			state.markLoaded(year.getKey(), List.copyOf(year.getValue()));
			yearsLoaded.add(year.getKey());
		}
		return new Outcome(current.size(), yearsLoaded, rowsWritten);
	}

	/** Years in ascending order; the counts of one year as a set, since only their content matters. */
	private static Map<Integer, Set<YearQualityCount>> byYear(List<YearQualityCount> counts, int firstYear) {
		return counts.stream().filter(count -> count.year() >= firstYear).collect(Collectors.groupingBy(
				YearQualityCount::year, TreeMap::new, Collectors.toCollection(HashSet::new)));
	}
}
