package com.twobgreen.gridintelligence.infrastructure.odre;

import static com.twobgreen.gridintelligence.infrastructure.odre.FakeOdreServer.fixture;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.twobgreen.gridintelligence.domain.GridSnapshot;
import com.twobgreen.gridintelligence.domain.Production;
import com.twobgreen.gridintelligence.domain.Quality;

class NationalRealtimeMappingTests {

	private static final Instant OBSERVED_AT = Instant.parse("2026-10-20T14:45:00Z");

	@Test
	void measuredRowGivesAFullSnapshot() {
		Optional<GridSnapshot> snapshot = NationalRealtimeMapping.toSnapshot(row("national-tr-measured.json"));

		assertThat(snapshot).contains(new GridSnapshot(
				"national",
				OBSERVED_AT,
				new BigDecimal("29"),
				49970,
				49950,
				49650,
				new Production(38499, 2632, 0, 37, null, 6225, 4656, 3768, 941),
				0,
				Quality.PROVISIONAL,
				false));
	}

	@ParameterizedTest
	@ValueSource(strings = { "national-tr-forecast-only.json", "national-tr-forecast-tomorrow.json" })
	void forecastOnlyRowGivesNoSnapshot(String fixture) {
		assertThat(NationalRealtimeMapping.toSnapshot(row(fixture))).isEmpty();
	}

	@Test
	void rowWithMissingValuesGivesAPartialSnapshot() {
		GridSnapshot snapshot = NationalRealtimeMapping.toSnapshot(row("national-tr-missing-values-synthetic.json"))
				.orElseThrow();

		assertThat(snapshot.observedAt()).isEqualTo(OBSERVED_AT);
		assertThat(snapshot.consumption()).isEqualTo(49970);
		// taux_co2 null, prevision_j " ", gaz "", eolien "ND", solaire "-", bioenergies "abc"
		assertThat(snapshot.co2Intensity()).isNull();
		assertThat(snapshot.consumptionForecastDay()).isNull();
		assertThat(snapshot.consumptionForecastDayBefore()).isEqualTo(49650);
		// hydraulique "3768" is a number sent as text, so it is kept
		assertThat(snapshot.production()).isEqualTo(new Production(38499, null, 0, 37, null, null, null, 3768, null));
		assertThat(snapshot.pumping()).isZero();
	}

	@Test
	void consumptionThatIsNotANumberCountsAsNotMeasured() {
		assertThat(NationalRealtimeMapping.toSnapshot(measured("ND", "-600"))).isEmpty();
	}

	@Test
	void negativePumpingIsKept() {
		assertThat(NationalRealtimeMapping.toSnapshot(measured("49970", "-2074")).orElseThrow().pumping())
				.isEqualTo(-2074);
	}

	@Test
	void positivePumpingIsDroppedButTheReadingIsKept() {
		GridSnapshot snapshot = NationalRealtimeMapping.toSnapshot(measured("49970", "350")).orElseThrow();

		assertThat(snapshot.pumping()).isNull();
		assertThat(snapshot.consumption()).isEqualTo(49970);
		assertThat(snapshot.co2Intensity()).isEqualByComparingTo("29");
	}

	@Test
	void decimalInAMegawattFieldIsMissingNotRounded() {
		// Looks like GW instead of MW: rounding would store a believable, 1000 times wrong value
		NationalRealtimeRecord row = new NationalRealtimeRecord(OBSERVED_AT, "49970", "29", null, null,
				"38.499", null, null, null, null, null, null, null, "0");

		assertThat(NationalRealtimeMapping.toSnapshot(row).orElseThrow().production().nuclear()).isNull();
		assertThat(NationalRealtimeMapping.toSnapshot(measured("49.97", "0"))).isEmpty();
	}

	private static NationalRealtimeRecord row(String fixture) {
		return new OdreResponses().nationalRealtime(fixture(fixture)).results().getFirst();
	}

	private static NationalRealtimeRecord measured(String consommation, String pompage) {
		return new NationalRealtimeRecord(OBSERVED_AT, consommation, "29", null, null,
				"38499", null, null, null, null, null, null, null, pompage);
	}
}
