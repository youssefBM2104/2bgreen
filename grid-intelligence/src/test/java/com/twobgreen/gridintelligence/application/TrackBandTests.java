package com.twobgreen.gridintelligence.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.twobgreen.gridintelligence.domain.Band;
import com.twobgreen.gridintelligence.domain.BandChange;
import com.twobgreen.gridintelligence.domain.BandPolicy;
import com.twobgreen.gridintelligence.domain.BandState;

/** The decisions of the band tracker, without Kafka: state in, state and event out. */
class TrackBandTests {

	private static final Instant T0 = Instant.parse("2026-10-20T12:00:00Z");

	private final TrackBand track = new TrackBand(new BandPolicy(BigDecimal.valueOf(20), BigDecimal.valueOf(40)));

	@Test
	void theFirstSnapshotOfAScopeIsAnnouncedWithNoPreviousBand() {
		TrackBand.Outcome outcome = track.observe("national", null, T0, BigDecimal.valueOf(15));

		assertThat(outcome.state()).isEqualTo(new BandState(Band.GREEN, T0));
		assertThat(outcome.change()).contains(
				new BandChange("national", Band.GREEN, null, T0, BigDecimal.valueOf(15)));
	}

	@Test
	void greenGreenAmberAmberGreenGivesExactlyThreeEvents() {
		List<BandChange> events = new ArrayList<>();
		BandState state = null;
		int[] co2 = { 10, 15, 30, 35, 12 };
		for (int i = 0; i < co2.length; i++) {
			TrackBand.Outcome outcome = track.observe("national", state, T0.plusSeconds(900L * i),
					BigDecimal.valueOf(co2[i]));
			state = outcome.state();
			outcome.change().ifPresent(events::add);
		}

		assertThat(events).extracting(BandChange::band, BandChange::previousBand).containsExactly(
				org.assertj.core.groups.Tuple.tuple(Band.GREEN, null),
				org.assertj.core.groups.Tuple.tuple(Band.AMBER, Band.GREEN),
				org.assertj.core.groups.Tuple.tuple(Band.GREEN, Band.AMBER));
		assertThat(events).extracting(BandChange::observedAt).containsExactly(T0, T0.plusSeconds(1800),
				T0.plusSeconds(3600));
	}

	@Test
	void aSnapshotInTheSameBandMovesTheStateButNotifiesNothing() {
		BandState previous = new BandState(Band.AMBER, T0);

		TrackBand.Outcome outcome = track.observe("national", previous, T0.plusSeconds(900), BigDecimal.valueOf(25));

		assertThat(outcome.change()).isEmpty();
		assertThat(outcome.state()).isEqualTo(new BandState(Band.AMBER, T0.plusSeconds(900)));
	}

	@Test
	void theSameSnapshotAgainIsIgnored() {
		BandState previous = new BandState(Band.GREEN, T0);

		// even if the value now falls in another band: the identity of a snapshot is its observedAt
		TrackBand.Outcome outcome = track.observe("national", previous, T0, BigDecimal.valueOf(90));

		assertThat(outcome.change()).isEmpty();
		assertThat(outcome.state()).isSameAs(previous);
	}

	@Test
	void anOlderSnapshotIsIgnoredAndNeverMovesTheStateBack() {
		BandState previous = new BandState(Band.RED, T0);

		TrackBand.Outcome outcome = track.observe("national", previous, T0.minusSeconds(900), BigDecimal.valueOf(5));

		assertThat(outcome.change()).isEmpty();
		assertThat(outcome.state()).isSameAs(previous);
	}

	@Test
	void aSnapshotWithoutCo2HasNoBandAndDoesNotCountAsSeen() {
		BandState previous = new BandState(Band.GREEN, T0);

		TrackBand.Outcome outcome = track.observe("region:76", previous, T0.plusSeconds(900), null);

		assertThat(outcome.change()).isEmpty();
		assertThat(outcome.state()).isSameAs(previous);
		assertThat(track.observe("region:76", null, T0, null).state()).isNull();
	}
}
