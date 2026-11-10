package com.twobgreen.gridintelligence.infrastructure.odre;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.twobgreen.gridintelligence.application.OdreException;
import com.twobgreen.gridintelligence.application.OdreException.Reason;

/**
 * HTTP client of the Explore v2.1 {@code records} endpoint. Returns DTOs, which never leave this package: the
 * mapping to the domain (D4) is what implements {@code OdreGateway}.
 * <p>
 * Paging is by cursor, not by offset: each request asks for the rows strictly after the last {@code date_heure}
 * received, ordered by {@code date_heure}. That avoids ODRÉ's cap on {@code offset} and stays correct if rows are
 * added between two pages. It relies on {@code date_heure} being unique per row, true for national datasets
 * (one row per period). Regional datasets have one row per region and period: see {@link #regionalRealtimeBetween}.
 */
class OdreClient {

	static final String NATIONAL_REALTIME = "eco2mix-national-tr";

	static final String REGIONAL_REALTIME = "eco2mix-regional-tr";

	static final String NATIONAL_CONSOLIDATED = "eco2mix-national-cons-def";

	private static final Logger log = LoggerFactory.getLogger(OdreClient.class);

	private static final String RECORDS = "/catalog/datasets/{dataset}/records"
			+ "?where={where}&order_by=date_heure&limit={limit}";

	// Whole result as JSON Lines in one call: records is capped at 100 rows a call, and every call costs the same
	private static final String EXPORT = "/catalog/datasets/{dataset}/exports/jsonl?where={where}";

	private static final String COUNTS = "/catalog/datasets/{dataset}/records"
			+ "?select={select}&group_by={groupBy}&limit={limit}";

	/** Rows per (year, nature) group fit in one page for a long time: 15 years of history, one new group a year. */
	private static final int COUNTS_LIMIT = 100;

	private static final int TOO_MANY_REQUESTS = 429;

	private final RestClient rest;

	private final int pageSize;

	private final OdreResponses responses = new OdreResponses();

	OdreClient(RestClient rest, int pageSize) {
		this.rest = rest;
		this.pageSize = pageSize;
	}

	static OdreClient create(OdreProperties properties) {
		HttpClient http = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(http);
		requestFactory.setReadTimeout(properties.readTimeout());
		RestClient rest = RestClient.builder()
				.baseUrl(properties.baseUrl().toString())
				.requestFactory(requestFactory)
				.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
				.build();
		return new OdreClient(rest, properties.pageSize());
	}

	/**
	 * Every row of {@code eco2mix-national-tr} with {@code date_heure} strictly between {@code after} and
	 * {@code before}, oldest first, all pages included. The upper bound keeps the forecast tail (future rows) out of
	 * the response, so a regular poll fits in one call; the few forecast rows before it are dropped by the mapping.
	 *
	 * @param before must be whole seconds: it goes into an ODSQL date literal
	 * @throws OdreException if a page cannot be read; rows of the previous pages are not returned
	 */
	List<NationalRealtimeRecord> nationalRealtimeBetween(Instant after, Instant before) {
		return between(NATIONAL_REALTIME, after, before, responses::nationalRealtime,
				NationalRealtimeRecord::dateHeure, false);
	}

	/**
	 * Same for {@code eco2mix-regional-tr}, every region together (one row per region and period, so the same
	 * {@code date_heure} comes up to 12 times). {@code date_heure} alone cannot say where a page stops inside such a
	 * group, so a full page drops its last, possibly incomplete, timestamp and the next page starts just before it:
	 * a period is never split between two pages. The page size must therefore hold at least one whole period.
	 *
	 * @param before must be whole seconds, see {@link #nationalRealtimeBetween}
	 * @throws OdreException if a page cannot be read, or if one page is smaller than one period
	 */
	List<RegionalRealtimeRecord> regionalRealtimeBetween(Instant after, Instant before) {
		return between(REGIONAL_REALTIME, after, before, responses::regionalRealtime,
				RegionalRealtimeRecord::dateHeure, true);
	}

	/**
	 * Every measured row of {@code eco2mix-national-cons-def} with {@code date_heure} from {@code from} (included) to
	 * {@code to} (excluded), in one {@code exports} call. The server drops the rows without consumption (the empty
	 * {@code :15} and {@code :45} placeholders), which halves the transfer. The order is not guaranteed and no caller
	 * may rely on it. A year is about 17,500 rows, so the batch asks one UTC year at a time.
	 *
	 * @param from must be whole seconds: it goes into an ODSQL date literal
	 * @param to same
	 * @throws OdreException if the export cannot be read; a line that is not valid fails the whole call, so a
	 *         half-read year is never returned
	 */
	List<NationalConsolidatedRecord> nationalConsolidatedBetween(Instant from, Instant to) {
		Objects.requireNonNull(from, "from");
		Objects.requireNonNull(to, "to");
		List<NationalConsolidatedRecord> rows = new ArrayList<>();
		if (!to.isAfter(from)) {
			return rows;
		}
		String where = "date_heure >= \"" + from + "\" and date_heure < \"" + to + "\" and consommation is not null";
		get(NATIONAL_CONSOLIDATED, from, EXPORT, new Object[] { NATIONAL_CONSOLIDATED, where }, body -> {
			try (BufferedReader lines = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
				String line;
				while ((line = lines.readLine()) != null) {
					if (!line.isBlank()) {
						rows.add(responses.nationalConsolidatedLine(line));
					}
				}
			}
			return rows;
		});
		log.debug("ODRÉ {}: {} rows between {} and {} in 1 export call", NATIONAL_CONSOLIDATED, rows.size(), from, to);
		return rows;
	}

	/**
	 * How many rows {@code eco2mix-national-cons-def} holds per UTC year and {@code nature}, in one call. The batch
	 * compares this with what it saw last time to know which years changed, instead of downloading everything. A
	 * year changes when rows are added to it, or when they move from consolidated to definitive, since the pair
	 * (year, nature) then loses and gains rows.
	 *
	 * @throws OdreException if the counts cannot be read, or if ODRÉ holds more groups than one page: a truncated
	 *         answer would hide a change
	 */
	List<ConsolidatedCount> nationalConsolidatedCounts() {
		String body = get(NATIONAL_CONSOLIDATED, Instant.EPOCH, COUNTS, new Object[] { NATIONAL_CONSOLIDATED,
				"annee,nature,count(*) as n", "year(date_heure) as annee,nature", COUNTS_LIMIT }, this::readText);
		try {
			RecordsPage<ConsolidatedCount> page = responses.nationalConsolidatedCounts(body);
			if (page.totalCount() > page.results().size()) {
				throw new OdreException(Reason.INVALID_RESPONSE,
						page.totalCount() + " groups but only " + page.results().size() + " returned");
			}
			return page.results();
		}
		catch (OdreException e) {
			throw failure(NATIONAL_CONSOLIDATED, Instant.EPOCH, e);
		}
	}

	/**
	 * Paging is by cursor on {@code date_heure}. With {@code manyRowsPerDate} a full page keeps only the periods it
	 * holds completely.
	 */
	private <T> List<T> between(String dataset, Instant after, Instant before,
			Function<String, RecordsPage<T>> decode, Function<T, Instant> dateOf, boolean manyRowsPerDate) {
		Objects.requireNonNull(after, "after");
		Objects.requireNonNull(before, "before");
		List<T> rows = new ArrayList<>();
		// Nothing can match: no call, the quota is spared
		if (!before.isAfter(after)) {
			return rows;
		}
		Instant cursor = after;
		int pages = 0;
		while (true) {
			RecordsPage<T> page = page(dataset, cursor, before, decode);
			pages++;
			List<T> results = page.results();
			if (results.size() < pageSize || results.size() >= page.totalCount()) {
				rows.addAll(results);
				log.debug("ODRÉ {}: {} rows between {} and {} in {} page(s)", dataset, rows.size(), after, before,
						pages);
				return rows;
			}
			if (manyRowsPerDate) {
				Instant lastDate = dateOf.apply(results.getLast());
				results = results.stream().filter(row -> dateOf.apply(row).isBefore(lastDate)).toList();
				if (results.isEmpty()) {
					throw failure(dataset, cursor, new OdreException(Reason.INVALID_RESPONSE,
							"a page of " + pageSize + " rows holds less than one period"));
				}
			}
			rows.addAll(results);
			Instant last = dateOf.apply(results.getLast());
			// A server ignoring the filter or the order would make the loop endless
			if (!last.isAfter(cursor)) {
				throw failure(dataset, cursor,
						new OdreException(Reason.INVALID_RESPONSE, "page does not move past " + cursor));
			}
			cursor = last;
		}
	}

	private <T> RecordsPage<T> page(String dataset, Instant after, Instant before,
			Function<String, RecordsPage<T>> decode) {
		String body = fetch(dataset, after, before);
		try {
			return decode.apply(body);
		}
		catch (OdreException e) {
			throw failure(dataset, after, e);
		}
	}

	private String fetch(String dataset, Instant after, Instant before) {
		String where = "date_heure > date'" + after + "' and date_heure < date'" + before + "'";
		return get(dataset, after, RECORDS, new Object[] { dataset, where, pageSize }, this::readText);
	}

	/** Reads the body of a response that passed the status checks. */
	@FunctionalInterface
	private interface BodyReader<T> {

		T read(InputStream body) throws IOException;
	}

	private String readText(InputStream body) throws IOException {
		return StreamUtils.copyToString(body, StandardCharsets.UTF_8);
	}

	/**
	 * One request. Only the dataset, the cursor and the status are logged: never the body. The status is judged
	 * before the body is read, so a refused call never reaches a decoder.
	 */
	private <T> T get(String dataset, Instant after, String uriTemplate, Object[] variables, BodyReader<T> reader) {
		try {
			return rest.get()
					.uri(uriTemplate, variables)
					.exchange((request, response) -> {
						HttpStatusCode status = response.getStatusCode();
						if (status.value() == TOO_MANY_REQUESTS) {
							throw new OdreException(Reason.QUOTA_EXCEEDED, "HTTP 429, call quota used up");
						}
						if (status.is5xxServerError()) {
							throw new OdreException(Reason.SERVER_ERROR, "HTTP " + status.value());
						}
						if (!status.is2xxSuccessful()) {
							throw new OdreException(Reason.REJECTED, "HTTP " + status.value());
						}
						return reader.read(response.getBody());
					});
		}
		catch (OdreException e) {
			throw failure(dataset, after, e);
		}
		catch (ResourceAccessException e) {
			throw failure(dataset, after, new OdreException(Reason.UNREACHABLE,
					"no response: " + e.getMostSpecificCause().getClass().getSimpleName(), e));
		}
	}

	private static OdreException failure(String dataset, Instant after, OdreException e) {
		log.warn("ODRÉ {} rows after {}: {} ({})", dataset, after, e.reason(), e.getMessage());
		return e;
	}
}
