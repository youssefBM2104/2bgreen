package com.twobgreen.gridintelligence.infrastructure.odre;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.twobgreen.gridintelligence.application.OdreException;
import com.twobgreen.gridintelligence.application.OdreException.Reason;
import com.twobgreen.gridintelligence.application.OdreGateway;
import com.twobgreen.gridintelligence.domain.GridSnapshot;

class NationalRealtimeGatewayTests {

	private static final Instant AFTER = Instant.parse("2026-10-20T14:15:00Z");

	private final FakeOdreServer odre = new FakeOdreServer();

	private final OdreGateway gateway = new NationalRealtimeGateway(OdreClient.create(
			new OdreProperties(odre.baseUrl(), Duration.ofSeconds(1), Duration.ofSeconds(1), 100)));

	@AfterEach
	void stopServer() {
		odre.close();
	}

	@Test
	void returnsMeasuredReadingsInOrderAndDropsTheForecastTail() {
		odre.reply(200, """
				{"total_count": 4, "results": [
				  {"date_heure": "2026-10-20T14:30:00+00:00", "consommation": 49800, "taux_co2": 30},
				  {"date_heure": "2026-10-20T14:45:00+00:00", "consommation": 49970, "taux_co2": 29},
				  {"date_heure": "2026-10-20T15:00:00+00:00", "consommation": null, "prevision_j": 49700, "prevision_j1": 49400},
				  {"date_heure": "2026-10-20T22:00:00+00:00", "consommation": null, "prevision_j1": 47700}
				]}
				""");

		assertThat(gateway.nationalReadingsAfter(AFTER))
				.extracting(GridSnapshot::observedAt, GridSnapshot::consumption)
				.containsExactly(
						tuple(Instant.parse("2026-10-20T14:30:00Z"), 49800),
						tuple(Instant.parse("2026-10-20T14:45:00Z"), 49970));
	}

	@Test
	void onlyForecastRowsGiveNoReading() {
		odre.reply(200, FakeOdreServer.fixture("national-tr-forecast-tomorrow.json"));

		assertThat(gateway.nationalReadingsAfter(AFTER)).isEmpty();
	}

	@Test
	void clientErrorsGoThroughUnchanged() {
		odre.reply(429, "{}");

		assertThatThrownBy(() -> gateway.nationalReadingsAfter(AFTER))
				.isInstanceOfSatisfying(OdreException.class, e -> assertThat(e.reason()).isEqualTo(Reason.QUOTA_EXCEEDED));
	}
}
