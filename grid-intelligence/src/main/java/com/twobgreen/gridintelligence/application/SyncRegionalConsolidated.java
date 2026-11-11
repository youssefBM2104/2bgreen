package com.twobgreen.gridintelligence.application;

import java.util.List;

import com.twobgreen.gridintelligence.domain.RegionalCo2Estimator;

/**
 * Use case of the regional batch (G6): bring {@code batch_snapshot} up to date with the consolidated and definitive
 * regional history of ODRÉ, from {@code firstYear}. Same mechanics as {@link SyncNationalConsolidated} (one cheap
 * count call, then only the years whose counts changed), on its own sync state.
 * <p>
 * Every reading gets the CO2 estimate of G7 before it is stored, like the speed layer does, so a region's history and
 * its provisional readings are the same quantity in the merged view.
 */
public class SyncRegionalConsolidated {

	/**
	 * What one run did, for the logs.
	 *
	 * @param yearsChecked years ODRÉ holds data for, from {@code firstYear}
	 * @param yearsLoaded years downloaded again, oldest first
	 * @param rowsWritten rows inserted or updated in the table (0 when nothing changed)
	 */
	public record Result(int yearsChecked, List<Integer> yearsLoaded, int rowsWritten) {
	}

	private final RegionalConsolidatedOdreGateway odre;

	private final BatchSnapshotRepository snapshots;

	private final BatchSyncState state;

	private final RegionalCo2Estimator co2;

	private final int firstYear;

	/**
	 * @param state the sync state of the regional dataset, separate from the national one
	 * @param firstYear first UTC year loaded; older years are ignored
	 */
	public SyncRegionalConsolidated(RegionalConsolidatedOdreGateway odre, BatchSnapshotRepository snapshots,
			BatchSyncState state, RegionalCo2Estimator co2, int firstYear) {
		this.odre = odre;
		this.snapshots = snapshots;
		this.state = state;
		this.co2 = co2;
		this.firstYear = firstYear;
	}

	/**
	 * One run, oldest year first. On failure the exception goes to the caller: the years already loaded stay recorded,
	 * so the next run resumes with the year that failed.
	 *
	 * @throws OdreException if ODRÉ cannot be read
	 * @throws RuntimeException if the readings cannot be stored
	 */
	public synchronized Result sync() {
		ConsolidatedYears.Outcome outcome = ConsolidatedYears.sync(odre.regionalCounts(), odre::regionalReadings,
				snapshots, state, firstYear, co2::estimate);
		return new Result(outcome.yearsChecked(), outcome.yearsLoaded(), outcome.rowsWritten());
	}
}
