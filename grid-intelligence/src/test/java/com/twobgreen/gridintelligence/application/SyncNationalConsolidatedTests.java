package com.twobgreen.gridintelligence.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.twobgreen.gridintelligence.application.OdreException.Reason;
import com.twobgreen.gridintelligence.domain.GridSnapshot;
import com.twobgreen.gridintelligence.domain.Quality;

class SyncNationalConsolidatedTests {

	private final FakeOdre odre = new FakeOdre();

	private final FakeSnapshots snapshots = new FakeSnapshots();

	private final FakeState state = new FakeState();

	private final SyncNationalConsolidated sync = new SyncNationalConsolidated(odre, snapshots, state);

	@Test
	void firstRunLoadsEveryYearOldestFirstAndRecordsThem() {
		odre.counts = List.of(count(2026, Quality.CONSOLIDATED, 100), count(2024, Quality.DEFINITIVE, 300),
				count(2025, Quality.DEFINITIVE, 200));

		SyncNationalConsolidated.Result result = sync.sync();

		assertThat(result.yearsChecked()).isEqualTo(3);
		assertThat(result.yearsLoaded()).containsExactly(2024, 2025, 2026);
		assertThat(odre.downloaded).containsExactly(2024, 2025, 2026);
		assertThat(result.rowsWritten()).isEqualTo(3);
		assertThat(state.loaded()).containsExactlyInAnyOrderElementsOf(odre.counts);
	}

	@Test
	void secondRunOnUnchangedCountsDownloadsNothing() {
		odre.counts = List.of(count(2025, Quality.DEFINITIVE, 200), count(2026, Quality.CONSOLIDATED, 100));
		sync.sync();
		odre.downloaded.clear();
		snapshots.upserts.clear();

		SyncNationalConsolidated.Result result = sync.sync();

		assertThat(result.yearsLoaded()).isEmpty();
		assertThat(result.rowsWritten()).isZero();
		assertThat(odre.downloaded).isEmpty();
		assertThat(snapshots.upserts).isEmpty();
	}

	@Test
	void aYearWithNewRowsIsLoadedAgainAndTheOthersAreNot() {
		odre.counts = List.of(count(2025, Quality.DEFINITIVE, 200), count(2026, Quality.CONSOLIDATED, 100));
		sync.sync();
		odre.downloaded.clear();

		// September lands in cons-def: 2026 grows
		odre.counts = List.of(count(2025, Quality.DEFINITIVE, 200), count(2026, Quality.CONSOLIDATED, 130));

		assertThat(sync.sync().yearsLoaded()).containsExactly(2026);
		assertThat(odre.downloaded).containsExactly(2026);
	}

	@Test
	void aYearThatTurnsDefinitiveIsLoadedAgainEvenIfItsRowCountIsTheSame() {
		odre.counts = List.of(count(2026, Quality.CONSOLIDATED, 100));
		sync.sync();
		odre.downloaded.clear();

		// Same number of rows, new quality: the quality flip alone is the signal
		odre.counts = List.of(count(2026, Quality.DEFINITIVE, 100));

		assertThat(sync.sync().yearsLoaded()).containsExactly(2026);
		assertThat(state.loaded()).containsExactly(count(2026, Quality.DEFINITIVE, 100));
	}

	@Test
	void whenAYearTurnsDefinitiveItsLeftoverHourInThePreviousUtcYearIsLoadedToo() {
		// 2025 holds 4 consolidated rows: the first Paris hour of 2026
		odre.counts = List.of(count(2025, Quality.CONSOLIDATED, 4), count(2025, Quality.DEFINITIVE, 35036),
				count(2026, Quality.CONSOLIDATED, 23324));
		sync.sync();
		odre.downloaded.clear();

		odre.counts = List.of(count(2025, Quality.DEFINITIVE, 35040), count(2026, Quality.DEFINITIVE, 23324));

		assertThat(sync.sync().yearsLoaded()).containsExactly(2025, 2026);
		// The quality that left the source no longer lingers in the memory of the batch
		assertThat(state.loaded()).containsExactlyInAnyOrder(count(2025, Quality.DEFINITIVE, 35040),
				count(2026, Quality.DEFINITIVE, 23324));
	}

	@Test
	void aFailedYearIsNotRecordedAndTheNextRunResumesThere() {
		odre.counts = List.of(count(2024, Quality.DEFINITIVE, 300), count(2025, Quality.DEFINITIVE, 200),
				count(2026, Quality.CONSOLIDATED, 100));
		odre.failOn = 2025;

		assertThatThrownBy(sync::sync).isInstanceOf(OdreException.class);

		// 2024 stays recorded: its work is not lost
		assertThat(state.loaded()).containsExactly(count(2024, Quality.DEFINITIVE, 300));
		odre.failOn = null;
		odre.downloaded.clear();

		assertThat(sync.sync().yearsLoaded()).containsExactly(2025, 2026);
		assertThat(odre.downloaded).containsExactly(2025, 2026);
	}

	@Test
	void aYearIsRecordedOnlyOnceItsReadingsAreStored() {
		odre.counts = List.of(count(2026, Quality.CONSOLIDATED, 100));
		snapshots.failing = true;

		assertThatThrownBy(sync::sync).isInstanceOf(IllegalStateException.class);

		// Not recorded: the next run loads the year again instead of believing it is there
		assertThat(state.loaded()).isEmpty();
	}

	@Test
	void nothingAtOdreMeansNothingToDo() {
		odre.counts = List.of();

		SyncNationalConsolidated.Result result = sync.sync();

		assertThat(result.yearsChecked()).isZero();
		assertThat(result.yearsLoaded()).isEmpty();
		assertThat(odre.downloaded).isEmpty();
	}

	@Test
	void theCountsAreReadBeforeTheDownloadSoAChangeDuringItIsNotMissed() {
		odre.counts = List.of(count(2026, Quality.CONSOLIDATED, 100));
		odre.onDownload = () -> odre.counts = List.of(count(2026, Quality.CONSOLIDATED, 101));

		sync.sync();

		// What was recorded is what ODRÉ showed before the download: the next run sees 101 differs from 100
		assertThat(state.loaded()).containsExactly(count(2026, Quality.CONSOLIDATED, 100));
		odre.onDownload = () -> { };
		assertThat(sync.sync().yearsLoaded()).containsExactly(2026);
	}

	private static YearQualityCount count(int year, Quality quality, long rows) {
		return new YearQualityCount(year, quality, rows);
	}

	private static final class FakeOdre implements ConsolidatedOdreGateway {

		List<YearQualityCount> counts = List.of();

		final List<Integer> downloaded = new ArrayList<>();

		Integer failOn;

		Runnable onDownload = () -> { };

		@Override
		public List<YearQualityCount> nationalCounts() {
			return counts;
		}

		@Override
		public List<GridSnapshot> nationalReadings(int year) {
			if (failOn != null && failOn == year) {
				throw new OdreException(Reason.SERVER_ERROR, "HTTP 503");
			}
			downloaded.add(year);
			onDownload.run();
			return List.of(new GridSnapshot("national", Instant.parse(year + "-01-01T00:00:00Z"), null, 1, null,
					null, null, null, Quality.CONSOLIDATED, false));
		}
	}

	private static final class FakeSnapshots implements BatchSnapshotRepository {

		final List<List<GridSnapshot>> upserts = new ArrayList<>();

		boolean failing;

		@Override
		public int upsert(List<GridSnapshot> snapshots) {
			if (failing) {
				throw new IllegalStateException("database down");
			}
			upserts.add(snapshots);
			return snapshots.size();
		}
	}

	private static final class FakeState implements BatchSyncState {

		final Map<Integer, List<YearQualityCount>> byYear = new HashMap<>();

		@Override
		public List<YearQualityCount> loaded() {
			return byYear.values().stream().flatMap(List::stream).toList();
		}

		@Override
		public void markLoaded(int year, List<YearQualityCount> counts) {
			byYear.put(year, counts);
		}
	}
}
