package com.twobgreen.gridintelligence.infrastructure.odre;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.twobgreen.gridintelligence.application.OdreException;
import com.twobgreen.gridintelligence.application.OdreException.Reason;
import com.twobgreen.gridintelligence.application.RegionalOdreGateway;
import com.twobgreen.gridintelligence.domain.GridSnapshot;

class RegionalRealtimeGatewayTests {

	private static final Instant AFTER = Instant.parse("2026-10-20T14:15:00Z");

	private static final Clock NOW = Clock.fixed(Instant.parse("2026-10-20T15:05:12.345Z"), ZoneOffset.UTC);

	private final FakeOdreServer odre = new FakeOdreServer();

	private final RegionalOdreGateway gateway = new RegionalRealtimeGateway(OdreClient.create(
			new OdreProperties(odre.baseUrl(), Duration.ofSeconds(1), Duration.ofSeconds(1), 100)), NOW);

	@AfterEach
	void stopServer() {
		odre.close();
	}

	@Test
	void asksTheRegionalDatasetForEveryRegionUpToNow() {
		odre.reply(200, """
				{"total_count": 0, "results": []}
				""");

		gateway.regionalReadingsAfter(AFTER);

		assertThat(odre.requests()).singleElement().satisfies(request -> {
			assertThat(request.getPath()).isEqualTo("/api/explore/v2.1/catalog/datasets/eco2mix-regional-tr/records");
			// No region filter: one call serves the 12 regions
			assertThat(request.getQuery())
					.isEqualTo("where=date_heure > date'2026-10-20T14:15:00Z' and date_heure < date'2026-10-20T15:05:12Z'"
							+ "&order_by=date_heure&limit=100");
		});
	}

	@Test
	void returnsMeasuredReadingsOfEveryRegionAndDropsThePendingOnes() {
		odre.reply(200, """
				{"total_count": 4, "results": [
				  {"date_heure": "2026-10-20T14:30:00+00:00", "code_insee_region": "32", "consommation": 5500},
				  {"date_heure": "2026-10-20T14:30:00+00:00", "code_insee_region": "76", "consommation": 3800},
				  {"date_heure": "2026-10-20T14:45:00+00:00", "code_insee_region": "32", "consommation": 5539},
				  {"date_heure": "2026-10-20T14:45:00+00:00", "code_insee_region": "76", "consommation": null}
				]}
				""");

		assertThat(gateway.regionalReadingsAfter(AFTER))
				.extracting(GridSnapshot::scope, GridSnapshot::observedAt, GridSnapshot::consumption)
				.containsExactly(
						tuple("region:32", Instant.parse("2026-10-20T14:30:00Z"), 5500),
						tuple("region:76", Instant.parse("2026-10-20T14:30:00Z"), 3800),
						tuple("region:32", Instant.parse("2026-10-20T14:45:00Z"), 5539));
	}

	@Test
	void noCo2IsEverInvented() {
		odre.reply(200, """
				{"total_count": 1, "results": [
				  {"date_heure": "2026-10-20T14:30:00+00:00", "code_insee_region": "32", "consommation": 5500,
				   "taux_co2": 99, "prevision_j": 5400}
				]}
				""");

		assertThat(gateway.regionalReadingsAfter(AFTER)).singleElement().satisfies(snapshot -> {
			assertThat(snapshot.co2Intensity()).isNull();
			assertThat(snapshot.estimated()).isFalse();
			assertThat(snapshot.consumptionForecastDay()).isNull();
		});
	}

	@Test
	void clientErrorsGoThroughUnchanged() {
		odre.reply(429, "{}");

		assertThatThrownBy(() -> gateway.regionalReadingsAfter(AFTER))
				.isInstanceOfSatisfying(OdreException.class, e -> assertThat(e.reason()).isEqualTo(Reason.QUOTA_EXCEEDED));
	}
}
