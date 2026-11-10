package com.twobgreen.gridintelligence.infrastructure.odre;

import static com.twobgreen.gridintelligence.infrastructure.odre.FakeOdreServer.fixture;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.twobgreen.gridintelligence.domain.GridSnapshot;
import com.twobgreen.gridintelligence.domain.Production;
import com.twobgreen.gridintelligence.domain.Quality;

class RegionalRealtimeMappingTests {

	private static final Instant OBSERVED_AT = Instant.parse("2026-10-20T14:45:00Z");

	@Test
	void measuredRowGivesARegionalSnapshotWithoutCo2() {
		Optional<GridSnapshot> snapshot = RegionalRealtimeMapping.toSnapshot(row("regional-tr-measured.json"));

		// Hauts-de-France: scope from the INSEE code, fossil aggregate in thermal, no CO2, no forecasts,
		// pompage "0" sent as text is kept
		assertThat(snapshot).contains(new GridSnapshot(
				"region:32",
				OBSERVED_AT,
				null,
				5539,
				null,
				null,
				new Production(3562, null, null, null, 1173, 1374, 50, 3, 79),
				0,
				Quality.PROVISIONAL,
				false));
	}

	@Test
	void pendingRowGivesNoSnapshot() {
		assertThat(RegionalRealtimeMapping.toSnapshot(row("regional-tr-pending.json"))).isEmpty();
	}

	@Test
	void rowWithMissingValuesGivesAPartialSnapshot() {
		GridSnapshot snapshot = RegionalRealtimeMapping.toSnapshot(row("regional-tr-missing-values-synthetic.json"))
				.orElseThrow();

		assertThat(snapshot.scope()).isEqualTo("region:76");
		assertThat(snapshot.consumption()).isEqualTo(3805);
		// thermique "", nucleaire "ND", eolien "-", solaire "abc", hydraulique "1167" (text), bioenergies absent
		assertThat(snapshot.production()).isEqualTo(new Production(null, null, null, null, null, null, null, 1167, null));
		assertThat(snapshot.pumping()).isNull();
		assertThat(snapshot.co2Intensity()).isNull();
	}

	@Test
	void consumptionThatIsNotANumberCountsAsNotMeasured() {
		assertThat(RegionalRealtimeMapping.toSnapshot(record("76", "ND", "0"))).isEmpty();
	}

	@Test
	void rowWithoutRegionCodeCannotBeAttributedAndIsDropped() {
		assertThat(RegionalRealtimeMapping.toSnapshot(record(null, "3805", "0"))).isEmpty();
		assertThat(RegionalRealtimeMapping.toSnapshot(record("  ", "3805", "0"))).isEmpty();
	}

	@Test
	void positivePumpingIsDroppedButTheReadingIsKept() {
		GridSnapshot snapshot = RegionalRealtimeMapping.toSnapshot(record("76", "3805", "12")).orElseThrow();

		assertThat(snapshot.pumping()).isNull();
		assertThat(snapshot.consumption()).isEqualTo(3805);
	}

	@Test
	void negativePumpingIsKept() {
		assertThat(RegionalRealtimeMapping.toSnapshot(record("76", "3805", "-40")).orElseThrow().pumping())
				.isEqualTo(-40);
	}

	private static RegionalRealtimeRecord row(String fixture) {
		return new OdreResponses().regionalRealtime(fixture(fixture)).results().getFirst();
	}

	private static RegionalRealtimeRecord record(String code, String consommation, String pompage) {
		return new RegionalRealtimeRecord(OBSERVED_AT, code, consommation, null, null, null, null, null, null,
				pompage);
	}
}
