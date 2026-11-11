package com.twobgreen.gridintelligence.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.twobgreen.gridintelligence.application.OdreException.Reason;
import com.twobgreen.gridintelligence.domain.GridSnapshot;
import com.twobgreen.gridintelligence.domain.Production;
import com.twobgreen.gridintelligence.domain.Quality;
import com.twobgreen.gridintelligence.domain.RegionalCo2Estimator;

/** The regional poller's rules without Spring, Kafka or ODRÉ. */
class PollRegionalReadingsTests {

	private static final Duration OVERLAP = Duration.ofHours(3);

	private static final Instant FIRST_START = Instant.parse("2026-08-31T22:00:00Z");

	private static final Instant T1445 = Instant.parse("2026-10-20T14:45:00Z");

	private static final Instant T1500 = Instant.parse("2026-10-20T15:00:00Z");

	private final FakeOdre odre = new FakeOdre();

	private final FakePublisher publisher = new FakePublisher();

	private final FakeStore store = new FakeStore();

	private final PollRegionalReadings polls = new PollRegionalReadings(odre, publisher, store,
			new RegionalCo2Estimator(), OVERLAP, FIRST_START);

	@Test
	void emptyDatabaseStartsAtTheFirstStart() {
		odre.answer(List.of());

		polls.poll();

		assertThat(odre.asked).containsExactly(FIRST_START.minus(OVERLAP));
	}

	@Test
	void afterARestartResumesWhereTheStoredRegionalDataEnds() {
		store.latest = T1445;
		odre.answer(List.of());

		polls.poll();

		assertThat(odre.asked).containsExactly(T1445.minus(OVERLAP));
	}

	@Test
	void publishesEveryRegionsReadingAndMovesTheCursorToTheNewest() {
		odre.answer(List.of(reading("region:32", T1445), reading("region:76", T1445), reading("region:32", T1500)))
				.answer(List.of());

		PollRegionalReadings.Result result = polls.poll();
		polls.poll();

		assertThat(publisher.published).extracting(GridSnapshot::scope).containsExactly("region:32", "region:76",
				"region:32");
		assertThat(result.published()).isEqualTo(3);
		assertThat(odre.asked).containsExactly(FIRST_START.minus(OVERLAP), T1500.minus(OVERLAP));
	}

	@Test
	void readingsArePublishedWithTheirEstimatedCo2() {
		GridSnapshot complete = new GridSnapshot("region:32", T1445, null, 5539, null, null,
				new Production(3562, null, null, null, 1173, 1374, 50, 3, 79), 0, Quality.PROVISIONAL, false);
		GridSnapshot hydroMissing = new GridSnapshot("region:76", T1445, null, 3805, null, null,
				new Production(1257, null, null, null, 1, 376, 0, null, 0), null, Quality.PROVISIONAL, false);
		odre.answer(List.of(complete, hydroMissing));

		polls.poll();

		assertThat(publisher.published).extracting(GridSnapshot::co2Intensity)
				.containsExactly(BigDecimal.valueOf(87), null);
		assertThat(publisher.published).extracting(GridSnapshot::estimated).containsExactly(true, false);
	}

	@Test
	void overlapRowsDoNotMoveTheCursorBackwards() {
		store.latest = T1500;
		odre.answer(List.of(reading("region:32", T1445))).answer(List.of());

		polls.poll();
		polls.poll();

		assertThat(odre.asked).containsExactly(T1500.minus(OVERLAP), T1500.minus(OVERLAP));
	}

	@Test
	void odreFailurePublishesNothingAndTheNextPollAsksForTheSameRange() {
		store.latest = T1445;
		odre.fail(new OdreException(Reason.UNREACHABLE, "timeout")).answer(List.of(reading("region:32", T1500)));

		assertThatThrownBy(polls::poll).isInstanceOf(OdreException.class);
		assertThat(publisher.published).isEmpty();

		polls.poll();

		assertThat(odre.asked).containsExactly(T1445.minus(OVERLAP), T1445.minus(OVERLAP));
		assertThat(publisher.published).hasSize(1);
	}

	@Test
	void publishFailureKeepsTheCursorSoTheReadingsAreSentAgain() {
		store.latest = T1445;
		odre.answer(List.of(reading("region:32", T1500))).answer(List.of(reading("region:32", T1500)));
		publisher.failNext = true;

		assertThatThrownBy(polls::poll).isInstanceOf(IllegalStateException.class);
		polls.poll();

		assertThat(odre.asked).containsExactly(T1445.minus(OVERLAP), T1445.minus(OVERLAP));
		assertThat(publisher.published).hasSize(1);
	}

	private static GridSnapshot reading(String scope, Instant observedAt) {
		return new GridSnapshot(scope, observedAt, null, 5539, null, null, null, null, Quality.PROVISIONAL, false);
	}

	private static class FakeOdre implements RegionalOdreGateway {

		final List<Instant> asked = new ArrayList<>();

		private final Deque<Object> script = new ArrayDeque<>();

		FakeOdre answer(List<GridSnapshot> readings) {
			script.add(readings);
			return this;
		}

		FakeOdre fail(OdreException e) {
			script.add(e);
			return this;
		}

		@Override
		@SuppressWarnings("unchecked")
		public List<GridSnapshot> regionalReadingsAfter(Instant after) {
			asked.add(after);
			Object next = script.remove();
			if (next instanceof OdreException e) {
				throw e;
			}
			return (List<GridSnapshot>) next;
		}
	}

	private static class FakePublisher implements SnapshotPublisher {

		final List<GridSnapshot> published = new ArrayList<>();

		boolean failNext;

		@Override
		public void publish(List<GridSnapshot> snapshots) {
			if (failNext) {
				failNext = false;
				throw new IllegalStateException("Kafka down");
			}
			published.addAll(snapshots);
		}
	}

	private static class FakeStore implements SpeedSnapshotRepository {

		Instant latest;

		@Override
		public Instant upsert(GridSnapshot snapshot) {
			throw new UnsupportedOperationException("the poller never writes the speed table");
		}

		@Override
		public Optional<Instant> latestObservedAt(String scope) {
			throw new UnsupportedOperationException("the regional poller only reads regional scopes");
		}

		@Override
		public Optional<Instant> latestRegionalObservedAt() {
			return Optional.ofNullable(latest);
		}
	}
}
