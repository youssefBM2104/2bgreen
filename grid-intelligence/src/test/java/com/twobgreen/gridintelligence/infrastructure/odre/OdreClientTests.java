package com.twobgreen.gridintelligence.infrastructure.odre;

import static com.twobgreen.gridintelligence.infrastructure.odre.FakeOdreServer.fixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.twobgreen.gridintelligence.application.OdreException;
import com.twobgreen.gridintelligence.application.OdreException.Reason;

class OdreClientTests {

	private static final Instant AFTER = Instant.parse("2026-10-20T14:30:00Z");

	private static final Instant BEFORE = Instant.parse("2026-10-20T16:00:00Z");

	private static final Duration READ_TIMEOUT = Duration.ofMillis(500);

	private static final String EMPTY_PAGE = """
			{"total_count": 0, "results": []}
			""";

	private final FakeOdreServer odre = new FakeOdreServer();

	@AfterEach
	void stopServer() {
		odre.close();
	}

	@Test
	void decodesAPageOfFixturesIntoRecords() {
		odre.reply(200, fixture("national-tr-measured.json"));

		List<NationalRealtimeRecord> rows = client(100).nationalRealtimeBetween(AFTER, BEFORE);

		assertThat(rows).containsExactly(new NationalRealtimeRecord(
				Instant.parse("2026-10-20T14:45:00Z"),
				"49970", "29", "49950", "49650",
				"38499", "2632", "0", "37", "6225", "4656", "3768", "941", "0"));
	}

	@Test
	void asksForRowsBetweenTheTwoTimestampsOrderedByDate() {
		odre.reply(200, EMPTY_PAGE);

		client(100).nationalRealtimeBetween(AFTER, BEFORE);

		assertThat(odre.requests()).singleElement().satisfies(request -> {
			assertThat(request.getPath()).isEqualTo("/api/explore/v2.1/catalog/datasets/eco2mix-national-tr/records");
			assertThat(request.getQuery()).isEqualTo(query("2026-10-20T14:30:00Z", 100));
		});
	}

	@Test
	void emptyRangeMakesNoCall() {
		assertThat(client(100).nationalRealtimeBetween(BEFORE, BEFORE)).isEmpty();
		assertThat(client(100).nationalRealtimeBetween(BEFORE, AFTER)).isEmpty();
		assertThat(odre.requests()).isEmpty();
	}

	@Test
	void forecastOnlyRowsAreDecodedWithTheirMeasuresMissing() {
		odre.reply(200, fixture("national-tr-forecast-tomorrow.json"));

		NationalRealtimeRecord row = client(100).nationalRealtimeBetween(AFTER, BEFORE).getFirst();

		assertThat(row.dateHeure()).isEqualTo(Instant.parse("2026-10-20T22:00:00Z"));
		assertThat(row.previsionJ1()).isEqualTo("47700");
		assertThat(row.consommation()).isNull();
		assertThat(row.tauxCo2()).isNull();
		assertThat(row.previsionJ()).isNull();
	}

	@Test
	void keepsTheRawTextOfMeasuresWhateverTheirJsonType() {
		odre.reply(200, """
				{"total_count": 1, "results": [{
				  "date_heure": "2026-10-20T14:45:00+00:00",
				  "consommation": "49970", "eolien": "ND", "solaire": "-", "taux_co2": 29.5
				}]}
				""");

		NationalRealtimeRecord row = client(100).nationalRealtimeBetween(AFTER, BEFORE).getFirst();

		assertThat(row.consommation()).isEqualTo("49970");
		assertThat(row.eolien()).isEqualTo("ND");
		assertThat(row.solaire()).isEqualTo("-");
		assertThat(row.tauxCo2()).isEqualTo("29.5");
		assertThat(row.nucleaire()).isNull();
	}

	@Test
	void followsPagesFromTheLastDateReceived() {
		odre.reply(200, fixture("national-tr-measured.json"))
				.reply(200, fixture("national-tr-forecast-only.json"))
				.reply(200, EMPTY_PAGE);

		List<NationalRealtimeRecord> rows = client(1).nationalRealtimeBetween(AFTER, BEFORE);

		assertThat(rows).extracting(NationalRealtimeRecord::dateHeure).containsExactly(
				Instant.parse("2026-10-20T14:45:00Z"), Instant.parse("2026-10-20T15:00:00Z"));
		// The cursor moves, the upper bound stays
		assertThat(odre.requests()).extracting(request -> request.getQuery()).containsExactly(
				query("2026-10-20T14:30:00Z", 1),
				query("2026-10-20T14:45:00Z", 1),
				query("2026-10-20T15:00:00Z", 1));
	}

	@Test
	void stopsWithoutAnExtraCallWhenTheLastPageIsFull() {
		odre.reply(200, """
				{"total_count": 1, "results": [{"date_heure": "2026-10-20T14:45:00+00:00"}]}
				""");

		assertThat(client(1).nationalRealtimeBetween(AFTER, BEFORE)).hasSize(1);
		assertThat(odre.requests()).hasSize(1);
	}

	@Test
	void pageThatDoesNotMovePastTheCursorIsRejectedInsteadOfLoopingForever() {
		String stuck = """
				{"total_count": 50, "results": [{"date_heure": "2026-10-20T14:30:00+00:00"}]}
				""";
		odre.reply(200, stuck).reply(200, stuck);

		assertReason(() -> client(1).nationalRealtimeBetween(AFTER, BEFORE), Reason.INVALID_RESPONSE);
		assertThat(odre.requests()).hasSize(1);
	}

	@Test
	void http429MeansTheQuotaIsUsedUp() {
		odre.reply(429, """
				{"error_code": "TooManyRequests", "message": "Too many requests"}
				""");

		assertReason(() -> client(100).nationalRealtimeBetween(AFTER, BEFORE), Reason.QUOTA_EXCEEDED);
	}

	@Test
	void http500IsAServerError() {
		odre.reply(500, "Internal Server Error");

		assertReason(() -> client(100).nationalRealtimeBetween(AFTER, BEFORE), Reason.SERVER_ERROR);
	}

	@Test
	void otherHttp4xxMeansOurRequestWasRefused() {
		odre.reply(400, """
				{"error_code": "ODSQLError", "message": "ODSQL query is malformed"}
				""");

		assertReason(() -> client(100).nationalRealtimeBetween(AFTER, BEFORE), Reason.REJECTED);
	}

	@Test
	void bodyThatIsNotJsonIsInvalid() {
		odre.reply(200, "<html>maintenance</html>");

		assertReason(() -> client(100).nationalRealtimeBetween(AFTER, BEFORE), Reason.INVALID_RESPONSE);
	}

	@Test
	void jsonWithoutTheRecordsEnvelopeIsInvalid() {
		odre.reply(200, """
				{"total_count": 1}
				""");

		assertReason(() -> client(100).nationalRealtimeBetween(AFTER, BEFORE), Reason.INVALID_RESPONSE);
	}

	@Test
	void rowWithoutAValidDateIsInvalid() {
		odre.reply(200, """
				{"total_count": 1, "results": [{"date_heure": "20/10/2026 16:45", "consommation": 49970}]}
				""");

		assertReason(() -> client(100).nationalRealtimeBetween(AFTER, BEFORE), Reason.INVALID_RESPONSE);
	}

	@Test
	void noAnswerInTimeMeansUnreachable() {
		odre.replyAfter(READ_TIMEOUT.multipliedBy(4), 200, EMPTY_PAGE);

		assertReason(() -> client(100).nationalRealtimeBetween(AFTER, BEFORE), Reason.UNREACHABLE);
	}

	@Test
	void closedPortMeansUnreachable() {
		OdreClient client = client(100);
		odre.close();

		assertReason(() -> client.nationalRealtimeBetween(AFTER, BEFORE), Reason.UNREACHABLE);
	}

	@Test
	void errorMessagesNeverContainTheResponseBody() {
		odre.reply(500, "secret-looking body");

		assertThatThrownBy(() -> client(100).nationalRealtimeBetween(AFTER, BEFORE))
				.isInstanceOf(OdreException.class)
				.message().doesNotContain("secret-looking body");
	}

	/** The decoded query string the client must send, with {@link #BEFORE} as upper bound. */
	private static String query(String after, int limit) {
		return "where=date_heure > date'" + after + "' and date_heure < date'" + BEFORE + "'"
				+ "&order_by=date_heure&limit=" + limit;
	}

	private OdreClient client(int pageSize) {
		return OdreClient.create(new OdreProperties(odre.baseUrl(), Duration.ofSeconds(1), READ_TIMEOUT, pageSize));
	}

	private static void assertReason(Runnable call, Reason reason) {
		assertThatThrownBy(call::run)
				.isInstanceOfSatisfying(OdreException.class, e -> assertThat(e.reason()).isEqualTo(reason));
	}
}
