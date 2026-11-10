package com.twobgreen.gridintelligence.infrastructure.odre;

import static com.twobgreen.gridintelligence.infrastructure.odre.FakeOdreServer.fixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import com.twobgreen.gridintelligence.application.OdreException;
import com.twobgreen.gridintelligence.application.OdreException.Reason;
import com.twobgreen.gridintelligence.application.YearQualityCount;
import com.twobgreen.gridintelligence.domain.GridSnapshot;
import com.twobgreen.gridintelligence.domain.Quality;

class NationalConsolidatedGatewayTests {

	private final FakeOdreServer odre = new FakeOdreServer();

	@AfterEach
	void stopServer() {
		odre.close();
	}

	@Test
	void countsAreTranslatedToQualitiesAndOrderedOldestFirst() {
		odre.reply(200, fixture("national-consdef-counts.json"));

		List<YearQualityCount> counts = gateway().nationalCounts();

		assertThat(counts).hasSize(17);
		assertThat(counts.getFirst()).isEqualTo(new YearQualityCount(2011, Quality.DEFINITIVE, 4));
		assertThat(counts).containsSubsequence(
				new YearQualityCount(2025, Quality.CONSOLIDATED, 4),
				new YearQualityCount(2025, Quality.DEFINITIVE, 35036),
				new YearQualityCount(2026, Quality.CONSOLIDATED, 23324));
		assertThat(counts).extracting(YearQualityCount::year).isSorted();
	}

	@Test
	void countsWithAnUnknownNatureStopTheBatch() {
		odre.reply(200, """
				{"total_count": 1, "results": [{"annee": 2026, "nature": "Données provisoires", "n": 10}]}
				""");

		assertThatThrownBy(() -> gateway().nationalCounts()).isInstanceOfSatisfying(OdreException.class,
				e -> assertThat(e.reason()).isEqualTo(Reason.INVALID_RESPONSE));
	}

	@Test
	void aYearIsOneExportCallOverTheUtcYear() {
		odre.reply(200, fixture("national-consdef-export-sample.jsonl"));

		gateway().nationalReadings(2026);

		assertThat(odre.requests()).singleElement().satisfies(request -> assertThat(
				UriUtils.decode(UriComponentsBuilder.fromUri(request).build().getQueryParams().getFirst("where"),
						java.nio.charset.StandardCharsets.UTF_8))
				.startsWith("date_heure >= \"2026-01-01T00:00:00Z\" and date_heure < \"2027-01-01T00:00:00Z\""));
	}

	@Test
	void readingsAreConsolidatedSnapshotsOnTheHalfHour() {
		odre.reply(200, fixture("national-consdef-export-sample.jsonl"));

		List<GridSnapshot> readings = gateway().nationalReadings(2026);

		assertThat(readings).hasSize(3).allSatisfy(reading -> {
			assertThat(reading.scope()).isEqualTo("national");
			assertThat(reading.quality()).isEqualTo(Quality.CONSOLIDATED);
		});
	}

	@Test
	void springDstPhantomsDoNotReachTheCaller() {
		odre.reply(200, fixture("national-consdef-dst-spring-2026-trimmed.jsonl"));

		List<GridSnapshot> readings = gateway().nationalReadings(2026);

		assertThat(readings).extracting(GridSnapshot::observedAt).doesNotHaveDuplicates().hasSize(6);
	}

	@Test
	void anInstantThatComesBackTwiceIsRefusedInsteadOfGuessed() {
		// Both rows agree with their own date_heure, so no known anomaly explains the duplicate
		odre.reply(200, """
				{"nature": "Données consolidées", "date_heure": "2026-07-01T12:00:00+00:00", "heure": "14:00", "consommation": 1000}
				{"nature": "Données consolidées", "date_heure": "2026-07-01T12:00:00+00:00", "heure": "14:00", "consommation": 2000}
				""");

		assertThatThrownBy(() -> gateway().nationalReadings(2026)).isInstanceOfSatisfying(OdreException.class,
				e -> assertThat(e.reason()).isEqualTo(Reason.INVALID_RESPONSE));
	}

	private NationalConsolidatedGateway gateway() {
		return new NationalConsolidatedGateway(OdreClient.create(
				new OdreProperties(odre.baseUrl(), Duration.ofSeconds(1), Duration.ofMillis(500), 100)));
	}

}
