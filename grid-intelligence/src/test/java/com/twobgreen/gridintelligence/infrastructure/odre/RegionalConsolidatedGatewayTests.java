package com.twobgreen.gridintelligence.infrastructure.odre;

import static com.twobgreen.gridintelligence.infrastructure.odre.FakeOdreServer.fixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import com.twobgreen.gridintelligence.application.OdreException;
import com.twobgreen.gridintelligence.application.OdreException.Reason;
import com.twobgreen.gridintelligence.application.YearQualityCount;
import com.twobgreen.gridintelligence.domain.GridSnapshot;
import com.twobgreen.gridintelligence.domain.Production;
import com.twobgreen.gridintelligence.domain.Quality;

/**
 * The regional cons-def gateway (G6) on fixtures captured from ODRÉ on 2026-11-11: the counts of the whole dataset, and
 * the export of Occitanie and Provence-Alpes-Côte d'Azur on the morning of the 2025 spring DST change.
 */
class RegionalConsolidatedGatewayTests {

	private final FakeOdreServer odre = new FakeOdreServer();

	@AfterEach
	void stopServer() {
		odre.close();
	}

	private RegionalConsolidatedGateway gateway() {
		return new RegionalConsolidatedGateway(OdreClient.create(
				new OdreProperties(odre.baseUrl(), Duration.ofSeconds(1), Duration.ofSeconds(2), 100)));
	}

	@Test
	void countsAreTranslatedToQualitiesAndOrderedOldestFirst() {
		odre.reply(200, fixture("regional-consdef-counts.json"));

		List<YearQualityCount> counts = gateway().regionalCounts();

		assertThat(counts).extracting(YearQualityCount::year).isSorted();
		// 12 regions x 2 half hours of 2013-01-01 Paris that fall in UTC 2012
		assertThat(counts.getFirst()).isEqualTo(new YearQualityCount(2012, Quality.DEFINITIVE, 24));
		assertThat(counts).contains(new YearQualityCount(2013, Quality.DEFINITIVE, 210240));
		assertThat(counts).anyMatch(count -> count.year() == 2026 && count.quality() == Quality.CONSOLIDATED);
	}

	@Test
	void aYearIsOneExportCallOverTheUtcYearWithOnlyTheSnapshotColumns() {
		odre.reply(200, fixture("regional-consdef-export-dst-2025.jsonl"));

		gateway().regionalReadings(2025);

		assertThat(odre.requests()).singleElement().satisfies(request -> {
			assertThat(request.getPath()).endsWith("/catalog/datasets/eco2mix-regional-cons-def/exports/jsonl");
			MultiValueMap<String, String> query = UriComponentsBuilder.fromUri(request).build().getQueryParams();
			assertThat(decode(query.getFirst("where"))).startsWith(
					"date_heure >= \"2025-01-01T00:00:00Z\" and date_heure < \"2026-01-01T00:00:00Z\"");
			assertThat(decode(query.getFirst("select"))).isEqualTo(OdreClient.REGIONAL_CONSOLIDATED_FIELDS)
					.doesNotContain("tco_", "tch_");
		});
	}

	@Test
	void springDstPhantomsAreDroppedAndEveryRegionKeepsOneRowPerInstant() {
		odre.reply(200, fixture("regional-consdef-export-dst-2025.jsonl"));

		List<GridSnapshot> readings = gateway().regionalReadings(2025);

		// 12 rows in the export, 2 phantoms per region (02:00 and 02:30 do not exist in Paris that night)
		assertThat(readings).hasSize(8);
		assertThat(readings).extracting(GridSnapshot::scope).containsOnly("region:76", "region:93");
		assertThat(readings).filteredOn(r -> r.scope().equals("region:93")
				&& r.observedAt().equals(Instant.parse("2025-03-30T01:30:00Z")))
				.singleElement().extracting(GridSnapshot::consumption).isEqualTo(4298);
	}

	@Test
	void readingsAreDefinitiveRegionalSnapshotsWithoutCo2() {
		odre.reply(200, fixture("regional-consdef-export-dst-2025.jsonl"));

		GridSnapshot paca = gateway().regionalReadings(2025).stream()
				.filter(r -> r.scope().equals("region:93") && r.observedAt().equals(Instant.parse("2025-03-30T00:00:00Z")))
				.findFirst().orElseThrow();

		assertThat(paca.quality()).isEqualTo(Quality.DEFINITIVE);
		assertThat(paca.consumption()).isEqualTo(4257);
		assertThat(paca.co2Intensity()).isNull();
		assertThat(paca.estimated()).isFalse();
		Production production = paca.production();
		assertThat(production.thermal()).isEqualTo(126);
		assertThat(production.gas()).isNull();
		// eolien comes as text in this dataset
		assertThat(production.wind()).isNotNull();
	}

	@Test
	void twoRowsForOneRegionAndInstantStopTheBatch() {
		String row = """
				{"date_heure": "2025-06-01T10:00:00+00:00", "heure": "12:00", "nature": "Données définitives", \
				"code_insee_region": "93", "consommation": 4000}
				""";
		odre.reply(200, row + row);

		assertThatThrownBy(() -> gateway().regionalReadings(2025)).isInstanceOfSatisfying(OdreException.class,
				e -> assertThat(e.reason()).isEqualTo(Reason.INVALID_RESPONSE));
	}

	@Test
	void theSameInstantInTwoRegionsIsNoDuplicate() {
		odre.reply(200, """
				{"date_heure": "2025-06-01T10:00:00+00:00", "heure": "12:00", "nature": "Données définitives", "code_insee_region": "93", "consommation": 4000}
				{"date_heure": "2025-06-01T10:00:00+00:00", "heure": "12:00", "nature": "Données définitives", "code_insee_region": "76", "consommation": 3000}
				""");

		assertThat(gateway().regionalReadings(2025)).hasSize(2);
	}

	private static String decode(String value) {
		return UriUtils.decode(value, StandardCharsets.UTF_8);
	}
}
