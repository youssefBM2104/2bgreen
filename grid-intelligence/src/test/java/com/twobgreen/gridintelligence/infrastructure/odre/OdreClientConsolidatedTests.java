package com.twobgreen.gridintelligence.infrastructure.odre;

import static com.twobgreen.gridintelligence.infrastructure.odre.FakeOdreServer.fixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import com.twobgreen.gridintelligence.application.OdreException;
import com.twobgreen.gridintelligence.application.OdreException.Reason;

/** Batch side of {@link OdreClient}: the {@code exports} call and the change-detection counts. */
class OdreClientConsolidatedTests {

	private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");

	private static final Instant TO = Instant.parse("2027-01-01T00:00:00Z");

	private final FakeOdreServer odre = new FakeOdreServer();

	@AfterEach
	void stopServer() {
		odre.close();
	}

	@Test
	void exportIsOneCallBoundedAndFilteredByTheServer() {
		odre.reply(200, fixture("national-consdef-export-sample.jsonl"));

		client().nationalConsolidatedBetween(FROM, TO);

		assertThat(odre.requests()).singleElement().satisfies(request -> {
			assertThat(request.getPath())
					.isEqualTo("/api/explore/v2.1/catalog/datasets/eco2mix-national-cons-def/exports/jsonl");
			assertThat(param(request, "where"))
					.isEqualTo("date_heure >= \"2026-01-01T00:00:00Z\" and date_heure < \"2027-01-01T00:00:00Z\""
							+ " and consommation is not null");
		});
	}

	@Test
	void exportDecodesEveryLineAndSkipsBlankOnes() {
		odre.reply(200, "\n" + fixture("national-consdef-export-sample.jsonl") + "\n\n");

		List<NationalConsolidatedRecord> rows = client().nationalConsolidatedBetween(FROM, TO);

		assertThat(rows).extracting(NationalConsolidatedRecord::dateHeure).containsExactly(
				Instant.parse("2026-08-31T21:30:00Z"),
				Instant.parse("2026-08-31T21:00:00Z"),
				Instant.parse("2026-08-31T20:30:00Z"));
		assertThat(rows.getFirst().nature()).isEqualTo("Données consolidées");
		assertThat(rows.getFirst().heure()).isEqualTo("23:30");
		assertThat(rows.getFirst().consommation()).isEqualTo("43979");
		// "172" and "1541" are sent as text by ODRÉ: the fields the snapshot keeps are read whatever their JSON type
		assertThat(rows.getFirst().gaz()).isEqualTo("3219");
	}

	@Test
	void exportOfAnEmptyBodyIsAnEmptyList() {
		odre.reply(200, "");

		assertThat(client().nationalConsolidatedBetween(FROM, TO)).isEmpty();
	}

	@Test
	void emptyRangeMakesNoCall() {
		assertThat(client().nationalConsolidatedBetween(TO, TO)).isEmpty();
		assertThat(client().nationalConsolidatedBetween(TO, FROM)).isEmpty();
		assertThat(odre.requests()).isEmpty();
	}

	@Test
	void aBadLineFailsTheWholeExportInsteadOfReturningAHalfYear() {
		odre.reply(200, fixture("national-consdef-export-sample.jsonl") + "{not json\n");

		assertThatThrownBy(() -> client().nationalConsolidatedBetween(FROM, TO))
				.isInstanceOfSatisfying(OdreException.class, e -> assertThat(e.reason()).isEqualTo(Reason.INVALID_RESPONSE));
	}

	@Test
	void exportMapsHttpFailuresToReasons() {
		odre.reply(429, "").reply(503, "").reply(400, "");

		assertThat(reasonOf(() -> client().nationalConsolidatedBetween(FROM, TO))).isEqualTo(Reason.QUOTA_EXCEEDED);
		assertThat(reasonOf(() -> client().nationalConsolidatedBetween(FROM, TO))).isEqualTo(Reason.SERVER_ERROR);
		assertThat(reasonOf(() -> client().nationalConsolidatedBetween(FROM, TO))).isEqualTo(Reason.REJECTED);
	}

	@Test
	void countsAreOneCallGroupedByYearAndNature() {
		odre.reply(200, fixture("national-consdef-counts.json"));

		List<ConsolidatedCount> counts = client().nationalConsolidatedCounts();

		assertThat(odre.requests()).singleElement().satisfies(request -> {
			assertThat(request.getPath())
					.isEqualTo("/api/explore/v2.1/catalog/datasets/eco2mix-national-cons-def/records");
			assertThat(param(request, "select")).isEqualTo("annee,nature,count(*) as n");
			assertThat(param(request, "group_by")).isEqualTo("year(date_heure) as annee,nature");
		});
		assertThat(counts).hasSize(17);
		assertThat(counts.getFirst()).isEqualTo(new ConsolidatedCount(2011, "Données définitives", 4));
		// Quality flips on Paris years but groups are UTC years: 2025 appears under both natures
		assertThat(counts).contains(
				new ConsolidatedCount(2025, "Données consolidées", 4),
				new ConsolidatedCount(2025, "Données définitives", 35036),
				new ConsolidatedCount(2026, "Données consolidées", 23324));
	}

	@Test
	void countsTruncatedByThePageSizeAreRefusedBecauseTheyWouldHideAChange() {
		odre.reply(200, """
				{"total_count": 150, "results": [{"annee": 2012, "nature": "Données définitives", "n": 35136}]}
				""");

		assertThat(reasonOf(() -> client().nationalConsolidatedCounts())).isEqualTo(Reason.INVALID_RESPONSE);
	}

	@Test
	void countGroupWithoutAYearIsRefused() {
		// What ODRÉ answers when the alias is missing: the year comes back null (seen on the real API)
		odre.reply(200, """
				{"total_count": 1, "results": [{"year(date_heure)": null, "nature": "Données définitives", "n": 4}]}
				""");

		assertThat(reasonOf(() -> client().nationalConsolidatedCounts())).isEqualTo(Reason.INVALID_RESPONSE);
	}

	/** A query parameter as the server decodes it. */
	private static String param(java.net.URI request, String name) {
		return UriUtils.decode(UriComponentsBuilder.fromUri(request).build().getQueryParams().getFirst(name),
				java.nio.charset.StandardCharsets.UTF_8);
	}

	private OdreClient client() {
		OdreProperties properties = new OdreProperties(odre.baseUrl(), Duration.ofSeconds(1),
				Duration.ofMillis(500), 100);
		return OdreClient.create(properties);
	}

	private static Reason reasonOf(Runnable call) {
		try {
			call.run();
		}
		catch (OdreException e) {
			return e.reason();
		}
		throw new AssertionError("no OdreException");
	}
}
