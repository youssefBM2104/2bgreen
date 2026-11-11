package com.twobgreen.platform.regional.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.twobgreen.platform.advisory.GridScope;
import com.twobgreen.platform.advisory.GridSnapshot;
import com.twobgreen.platform.advisory.GridSnapshots;
import com.twobgreen.platform.advisory.GridUnavailableException;

/** The rules of the regional dashboard (U11) without Spring or HTTP. */
class GetRegionalDashboardTests {

	private static final Instant NOW = Instant.parse("2026-11-11T19:00:00Z");

	private static final Duration MAX_AGE = Duration.ofHours(4);

	private final FakeGrid grid = new FakeGrid();

	private final GetRegionalDashboard dashboards = new GetRegionalDashboard(grid, Clock.fixed(NOW, ZoneOffset.UTC),
			MAX_AGE);

	@Test
	void showsTheLatestReadingOfTheRegionOfTheToken() {
		GridSnapshot paca = snapshot("region:93", "2026-11-11T17:45:00Z");
		grid.latest.put("region:93", paca);

		RegionalDashboard dashboard = dashboards.dashboard("93");

		assertThat(dashboard.regionCode()).isEqualTo("93");
		assertThat(dashboard.regionName()).isEqualTo("Provence-Alpes-Côte d'Azur");
		assertThat(dashboard.region()).isSameAs(paca);
		assertThat(dashboard.maxAge()).isEqualTo(MAX_AGE);
		assertThat(grid.askedLatest).containsExactly("region:93");
	}

	@Test
	void theNationIsTakenAtTheHalfHourOfTheRegionalReadingNotItsLatest() {
		grid.latest.put("region:93", snapshot("region:93", "2026-11-11T17:45:00Z"));
		GridSnapshot france = snapshot("national", "2026-11-11T17:30:00Z");
		grid.range = List.of(france);

		RegionalDashboard dashboard = dashboards.dashboard("93");

		assertThat(dashboard.national()).isSameAs(france);
		assertThat(grid.askedRanges).containsExactly(
				"national " + Instant.parse("2026-11-11T17:30:00Z") + " " + Instant.parse("2026-11-11T18:00:00Z"));
		assertThat(grid.askedLatest).doesNotContain("national");
	}

	@Test
	void aRegionalReadingOnTheHalfHourIsItsOwnHalfHour() {
		grid.latest.put("region:93", snapshot("region:93", "2026-11-11T18:00:00Z"));

		dashboards.dashboard("93");

		assertThat(grid.askedRanges).containsExactly(
				"national " + Instant.parse("2026-11-11T18:00:00Z") + " " + Instant.parse("2026-11-11T18:30:00Z"));
	}

	@Test
	void withoutNationalDataForThatHalfHourTheRegionIsStillShown() {
		grid.latest.put("region:93", snapshot("region:93", "2026-11-11T17:45:00Z"));
		grid.range = List.of();

		RegionalDashboard dashboard = dashboards.dashboard("93");

		assertThat(dashboard.region()).isNotNull();
		assertThat(dashboard.national()).isNull();
	}

	@Test
	void aReadingOlderThanTheRegionalLimitIsStaleButStillShown() {
		grid.latest.put("region:93", snapshot("region:93", "2026-11-11T14:45:00Z"));

		RegionalDashboard dashboard = dashboards.dashboard("93");

		assertThat(dashboard.stale()).isTrue();
		assertThat(dashboard.region().observedAt()).isEqualTo(Instant.parse("2026-11-11T14:45:00Z"));
	}

	@Test
	void hoursOfLagWithinTheRegionalLimitAreNormal() {
		// Three hours behind: the traffic light (PT2H) would call it stale, the regional dashboard does not
		grid.latest.put("region:93", snapshot("region:93", "2026-11-11T16:00:00Z"));

		assertThat(dashboards.dashboard("93").stale()).isFalse();
	}

	@Test
	void exactlyAtTheLimitIsNotStaleYet() {
		grid.latest.put("region:93", snapshot("region:93", "2026-11-11T15:00:00Z"));

		assertThat(dashboards.dashboard("93").stale()).isFalse();
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "94", "Corse", "region:93", "9", "093", "national" })
	void aTokenWithoutAKnownRegionIsRefusedBeforeAnyRead(String regionCode) {
		assertThatThrownBy(() -> dashboards.dashboard(regionCode)).isInstanceOf(RegionNotAssignedException.class);
		assertThat(grid.askedLatest).isEmpty();
		assertThat(grid.askedRanges).isEmpty();
	}

	@Test
	void aRegionWithoutDataIsNoData() {
		assertThatThrownBy(() -> dashboards.dashboard("93")).isInstanceOf(NoRegionalDataException.class);
	}

	@Test
	void unavailableGridDataGoesToTheCaller() {
		grid.failure = new GridUnavailableException("down");

		assertThatThrownBy(() -> dashboards.dashboard("93")).isInstanceOf(GridUnavailableException.class);
	}

	@Test
	void theLimitMustBePositive() {
		assertThatThrownBy(() -> new GetRegionalDashboard(grid, Clock.systemUTC(), Duration.ZERO))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new GetRegionalDashboard(grid, Clock.systemUTC(), null))
				.isInstanceOf(IllegalArgumentException.class);
	}

	private static GridSnapshot snapshot(String scope, String observedAt) {
		Instant at = Instant.parse(observedAt);
		return new GridSnapshot(new GridScope(scope), at, 4559,
				new GridSnapshot.Production(0, null, null, null, 1417, 25, 556, 1606, 138), new BigDecimal("181"),
				true, "PROVISIONAL", at.plusSeconds(3600));
	}

	private static class FakeGrid implements GridSnapshots {

		final Map<String, GridSnapshot> latest = new HashMap<>();

		List<GridSnapshot> range = List.of();

		final List<String> askedLatest = new ArrayList<>();

		final List<String> askedRanges = new ArrayList<>();

		RuntimeException failure;

		@Override
		public Optional<GridSnapshot> latest(GridScope scope) {
			if (failure != null) {
				throw failure;
			}
			askedLatest.add(scope.value());
			return Optional.ofNullable(latest.get(scope.value()));
		}

		@Override
		public List<GridSnapshot> between(GridScope scope, Instant from, Instant to) {
			askedRanges.add(scope.value() + " " + from + " " + to);
			return range;
		}
	}
}
