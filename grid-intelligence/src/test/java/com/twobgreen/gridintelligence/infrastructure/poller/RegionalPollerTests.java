package com.twobgreen.gridintelligence.infrastructure.poller;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.twobgreen.gridintelligence.application.OdreException;
import com.twobgreen.gridintelligence.application.OdreException.Reason;
import com.twobgreen.gridintelligence.application.PollRegionalReadings;
import com.twobgreen.gridintelligence.application.RegionalOdreGateway;
import com.twobgreen.gridintelligence.application.SnapshotPublisher;
import com.twobgreen.gridintelligence.application.SpeedSnapshotRepository;
import com.twobgreen.gridintelligence.domain.GridSnapshot;
import com.twobgreen.gridintelligence.domain.RegionalCo2Estimator;

/** Whatever breaks, a regional tick never throws either. */
class RegionalPollerTests {

	private static final SnapshotPublisher NO_OP = snapshots -> {
	};

	@Test
	void odreUnavailableIsLoggedNotThrown() {
		assertNeverThrows(after -> {
			throw new OdreException(Reason.SERVER_ERROR, "HTTP 503");
		}, NO_OP);
	}

	@Test
	void quotaExceededIsLoggedNotThrown() {
		assertNeverThrows(after -> {
			throw new OdreException(Reason.QUOTA_EXCEEDED, "HTTP 429");
		}, NO_OP);
	}

	@Test
	void kafkaUnavailableIsLoggedNotThrown() {
		assertNeverThrows(after -> List.of(), snapshots -> {
			throw new IllegalStateException("Kafka down");
		});
	}

	private static void assertNeverThrows(RegionalOdreGateway odre, SnapshotPublisher publisher) {
		RegionalPoller poller = new RegionalPoller(new PollRegionalReadings(odre, publisher, new EmptyStore(),
				new RegionalCo2Estimator(), Duration.ofHours(3), Instant.parse("2026-08-31T22:00:00Z")));

		assertThatCode(poller::poll).doesNotThrowAnyException();
		assertThatCode(poller::poll).doesNotThrowAnyException();
	}

	private static class EmptyStore implements SpeedSnapshotRepository {

		@Override
		public Instant upsert(GridSnapshot snapshot) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Optional<Instant> latestObservedAt(String scope) {
			return Optional.empty();
		}

		@Override
		public Optional<Instant> latestRegionalObservedAt() {
			return Optional.empty();
		}
	}
}
