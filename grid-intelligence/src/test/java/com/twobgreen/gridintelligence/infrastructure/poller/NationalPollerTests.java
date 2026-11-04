package com.twobgreen.gridintelligence.infrastructure.poller;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.twobgreen.gridintelligence.application.OdreException;
import com.twobgreen.gridintelligence.application.OdreException.Reason;
import com.twobgreen.gridintelligence.application.OdreGateway;
import com.twobgreen.gridintelligence.application.PollNationalReadings;
import com.twobgreen.gridintelligence.application.SnapshotPublisher;
import com.twobgreen.gridintelligence.application.SpeedSnapshotRepository;
import com.twobgreen.gridintelligence.domain.GridSnapshot;

/** Whatever breaks, a tick never throws: the scheduler and the application keep running. */
class NationalPollerTests {

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

	private static void assertNeverThrows(OdreGateway odre, SnapshotPublisher publisher) {
		NationalPoller poller = new NationalPoller(new PollNationalReadings(odre, publisher, new EmptyStore(),
				Duration.ofHours(1), Instant.parse("2026-08-31T22:00:00Z")));

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
	}
}
