package com.twobgreen.gridintelligence.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

/** The rules of a range request, without a database: what is refused, and what the reader is asked. */
class GetSnapshotRangeTests {

	private final StubSnapshotReader reader = StubSnapshotReader.range(List.of());

	private final GetSnapshotRange useCase = new GetSnapshotRange(reader);

	@Test
	void theReaderIsAskedForTheScopeAndThePeriodOfTheCaller() {
		SnapshotRange range = useCase.national("2026-10-20T00:00:00Z", "2026-10-21T00:00:00Z");

		assertThat(reader.rangeCalls).containsExactly("national|2026-10-20T00:00:00Z|2026-10-21T00:00:00Z");
		assertThat(range.scope()).isEqualTo("national");
		assertThat(range.from()).isEqualTo(Instant.parse("2026-10-20T00:00:00Z"));
		assertThat(range.to()).isEqualTo(Instant.parse("2026-10-21T00:00:00Z"));
	}

	@Test
	void aRegionIsReadUnderItsScope() {
		useCase.region("76", "2026-10-20T00:00:00Z", "2026-10-21T00:00:00Z");

		assertThat(reader.rangeCalls).containsExactly("region:76|2026-10-20T00:00:00Z|2026-10-21T00:00:00Z");
	}

	@Test
	void whatIsMissingOrMalformedOrOutOfOrderIsRefusedBeforeTheReader() {
		assertInvalid(null, "2026-10-21T00:00:00Z", "'from' is required.");
		assertInvalid(" ", "2026-10-21T00:00:00Z", "'from' is required.");
		assertInvalid("2026-10-20T00:00:00Z", null, "'to' is required.");
		assertInvalid("2026-10-20", "2026-10-21T00:00:00Z", "'from' must be a UTC date-time, ISO-8601 with Z.");
		assertInvalid("2026-10-20T00:00:00Z", "tomorrow", "'to' must be a UTC date-time, ISO-8601 with Z.");
		assertInvalid("2026-10-21T00:00:00Z", "2026-10-20T00:00:00Z", "'from' must be before 'to'.");
		assertInvalid("2026-10-21T00:00:00Z", "2026-10-21T00:00:00Z", "'from' must be before 'to'.");

		assertThat(reader.rangeCalls).isEmpty();
	}

	@Test
	void theLimitIs366DaysIncluded() {
		useCase.national("2025-01-01T00:00:00Z", "2026-01-02T00:00:00Z");

		assertThat(reader.rangeCalls).hasSize(1);
		assertThatThrownBy(() -> useCase.national("2025-01-01T00:00:00Z", "2026-01-02T00:00:01Z"))
				.isInstanceOf(InvalidRangeException.class)
				.hasMessage("The range is wider than 366 days.");
		assertThat(reader.rangeCalls).hasSize(1);
	}

	@Test
	void aRegionCodeThatIsNotAnInseeCodeIsRefusedBeforeThePeriodIsLookedAt() {
		assertThatThrownBy(() -> useCase.region("xx", "garbage", "garbage")).isInstanceOf(UnknownScopeException.class);
		assertThatThrownBy(() -> useCase.region("123", "2026-10-20T00:00:00Z", "2026-10-21T00:00:00Z"))
				.isInstanceOf(UnknownScopeException.class);
		assertThat(reader.rangeCalls).isEmpty();
	}

	private void assertInvalid(String from, String to, String message) {
		assertThatThrownBy(() -> useCase.national(from, to)).isInstanceOf(InvalidRangeException.class)
				.hasMessage(message);
	}
}
