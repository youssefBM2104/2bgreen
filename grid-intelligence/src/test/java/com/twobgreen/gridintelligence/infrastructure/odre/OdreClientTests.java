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

	@Test
	void decodesARegionalPageIntoRecords() {
		odre.reply(200, fixture("regional-tr-measured.json"));

		List<RegionalRealtimeRecord> rows = client(100).regionalRealtimeBetween(AFTER, BEFORE);

		assertThat(rows).containsExactly(new RegionalRealtimeRecord(
				Instant.parse("2026-10-20T14:45:00Z"), "32",
				"5539", "1173", "3562", "1374", "50", "3", "79", "0"));
	}

	@Test
	void regionalFullPageKeepsOnlyWholePeriodsAndTheNextPageRestartsBeforeTheCutPeriod() {
		// 3 regions, pages of 5 rows: page 1 ends in the middle of the 15:00 period
		odre.reply(200, regionalPage(8, "14:45:00/32,76,84", "15:00:00/32,76"))
				.reply(200, regionalPage(3, "15:00:00/32,76,84"));

		List<RegionalRealtimeRecord> rows = client(5).regionalRealtimeBetween(AFTER, BEFORE);

		assertThat(rows).extracting(row -> row.dateHeure().toString() + "/" + row.codeInseeRegion())
				.containsExactly(
						"2026-10-20T14:45:00Z/32", "2026-10-20T14:45:00Z/76", "2026-10-20T14:45:00Z/84",
						"2026-10-20T15:00:00Z/32", "2026-10-20T15:00:00Z/76", "2026-10-20T15:00:00Z/84");
		// The second request starts at 14:45, so the whole 15:00 period is asked again: nothing is lost, nothing doubled
		assertThat(odre.requests()).extracting(request -> request.getQuery()).containsExactly(
				query("2026-10-20T14:30:00Z", 5),
				query("2026-10-20T14:45:00Z", 5));
	}

	@Test
	void regionalPageSmallerThanOnePeriodIsRejectedInsteadOfLoopingForever() {
		odre.reply(200, regionalPage(50, "14:45:00/32,76,84"));

		assertReason(() -> client(3).regionalRealtimeBetween(AFTER, BEFORE), Reason.INVALID_RESPONSE);
		assertThat(odre.requests()).hasSize(1);
	}

	@Test
	void regionalEmptyRangeMakesNoCall() {
		assertThat(client(100).regionalRealtimeBetween(BEFORE, BEFORE)).isEmpty();
		assertThat(odre.requests()).isEmpty();
	}

	/** A page: {@code "HH:mm:ss/code,code"} groups, one row per code, all on 2026-10-20. */
	private static String regionalPage(int totalCount, String... periods) {
		StringBuilder rows = new StringBuilder();
		for (String period : periods) {
			String[] parts = period.split("/");
			for (String code : parts[1].split(",")) {
				if (!rows.isEmpty()) {
					rows.append(',');
				}
				rows.append("{\"date_heure\": \"2026-10-20T").append(parts[0]).append("+00:00\", ")
						.append("\"code_insee_region\": \"").append(code).append("\", \"consommation\": 1000}");
			}
		}
		return "{\"total_count\": " + totalCount + ", \"results\": [" + rows + "]}";
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
