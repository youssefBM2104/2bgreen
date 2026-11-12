package com.twobgreen.gridintelligence.infrastructure.odre;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

	static final String REGIONAL_CONSOLIDATED = "eco2mix-regional-cons-def";

	/** The columns of {@link RegionalConsolidatedRecord}, nothing more. */
	static final String REGIONAL_CONSOLIDATED_FIELDS = "date_heure,heure,nature,code_insee_region,consommation,"
			+ "thermique,nucleaire,eolien,solaire,hydraulique,bioenergies,pompage";

	private static final Logger log = LoggerFactory.getLogger(OdreClient.class);

	private static final String RECORDS = "/catalog/datasets/{dataset}/records"
			+ "?where={where}&order_by=date_heure&limit={limit}";

	// Whole result as JSON Lines in one call: records is capped at 100 rows a call, and every call costs the same
	private static final String EXPORT = "/catalog/datasets/{dataset}/exports/jsonl?where={where}";

	private static final String EXPORT_SELECT = "/catalog/datasets/{dataset}/exports/jsonl?select={select}&where={where}";

	private static final String COUNTS = "/catalog/datasets/{dataset}/records"
			+ "?select={select}&group_by={groupBy}&limit={limit}";

	private static final String COUNTS_WHERE = COUNTS + "&where={where}";

	private static final Pattern INSEE_CODE = Pattern.compile("\\d{2}");

	/** Rows per (year, nature) group fit in one page for a long time: 15 years of history, one new group a year. */
	private static final int COUNTS_LIMIT = 100;

	private static final int TOO_MANY_REQUESTS = 429;

	private final RestClient rest;

	/** For the whole-year exports only: same server, longer read timeout. */
	private final RestClient exports;

	private final int pageSize;

	private final OdreResponses responses = new OdreResponses();

	OdreClient(RestClient rest, RestClient exports, int pageSize) {
		this.rest = rest;
		this.exports = exports;
		this.pageSize = pageSize;
	}

	static OdreClient create(OdreProperties properties) {
		HttpClient http = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
		return new OdreClient(restClient(properties, http, properties.readTimeout()),
				restClient(properties, http, properties.exportReadTimeout()), properties.pageSize());
	}

	private static RestClient restClient(OdreProperties properties, HttpClient http, Duration readTimeout) {
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(http);
		requestFactory.setReadTimeout(readTimeout);
		return RestClient.builder()
				.baseUrl(properties.baseUrl().toString())
				.requestFactory(requestFactory)
				.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
				.build();
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
		return exportBetween(NATIONAL_CONSOLIDATED, EXPORT, null, from, to, null, responses::nationalConsolidatedLine);
	}

	/**
	 * Same for {@code eco2mix-regional-cons-def}, every region together: about 210,000 rows a year (12 regions, 30-min
	 * grain). Only the columns of the snapshot are asked for ({@code select}), which cuts the transfer by about two
	 * thirds: the {@code tco_*} / {@code tch_*} rates and the exchanges are not needed.
	 *
	 * @param from must be whole seconds: it goes into an ODSQL date literal
	 * @param to same
	 */
	List<RegionalConsolidatedRecord> regionalConsolidatedBetween(Instant from, Instant to) {
		return regionalConsolidatedBetween(from, to, List.of());
	}

	/**
	 * Same, for the listed regions only ({@code regions} empty means all). One region is about a twelfth of the rows,
	 * which is the difference between minutes and seconds on ODRÉ's side.
	 *
	 * @param regions INSEE codes, two digits each
	 * @throws IllegalArgumentException if a code is not two digits (it goes into an ODSQL literal)
	 */
	List<RegionalConsolidatedRecord> regionalConsolidatedBetween(Instant from, Instant to, List<String> regions) {
		return exportBetween(REGIONAL_CONSOLIDATED, EXPORT_SELECT, REGIONAL_CONSOLIDATED_FIELDS, from, to,
				regionCondition(regions), responses::regionalConsolidatedLine);
	}

	/**
	 * The ODSQL condition that keeps the listed regions, or null for all of them.
	 *
	 * @throws IllegalArgumentException if a code is not two digits
	 */
	static String regionCondition(List<String> regions) {
		if (regions == null || regions.isEmpty()) {
			return null;
		}
		for (String code : regions) {
			if (code == null || !INSEE_CODE.matcher(code).matches()) {
				throw new IllegalArgumentException("a region code is two digits, got: " + code);
			}
		}
		return regions.stream().map(code -> "code_insee_region = \"" + code + "\"")
				.collect(Collectors.joining(" or ", "(", ")"));
	}

	private <T> List<T> exportBetween(String dataset, String template, String select, Instant from, Instant to,
			String condition, Function<String, T> decode) {
		Objects.requireNonNull(from, "from");
		Objects.requireNonNull(to, "to");
		List<T> rows = new ArrayList<>();
		if (!to.isAfter(from)) {
			return rows;
		}
		String where = "date_heure >= \"" + from + "\" and date_heure < \"" + to + "\" and consommation is not null"
				+ (condition == null ? "" : " and " + condition);
		Object[] variables = select == null ? new Object[] { dataset, where } : new Object[] { dataset, select, where };
		get(exports, dataset, from, template, variables, body -> {
			try (BufferedReader lines = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
				String line;
				while ((line = lines.readLine()) != null) {
					if (!line.isBlank()) {
						rows.add(decode.apply(line));
					}
				}
			}
			return rows;
		});
		log.debug("ODRÉ {}: {} rows between {} and {} in 1 export call", dataset, rows.size(), from, to);
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
		return consolidatedCounts(NATIONAL_CONSOLIDATED);
	}

	/** Same for {@code eco2mix-regional-cons-def}, every region together. */
	List<ConsolidatedCount> regionalConsolidatedCounts() {
		return regionalConsolidatedCounts(List.of());
	}

	/**
	 * Same, for the listed regions only ({@code regions} empty means all). The counts must cover the same regions as
	 * the exports, or a change in a region that is not loaded would trigger a download for nothing.
	 */
	List<ConsolidatedCount> regionalConsolidatedCounts(List<String> regions) {
		return consolidatedCounts(REGIONAL_CONSOLIDATED, regionCondition(regions));
	}

	private List<ConsolidatedCount> consolidatedCounts(String dataset) {
		return consolidatedCounts(dataset, null);
	}

	private List<ConsolidatedCount> consolidatedCounts(String dataset, String condition) {
		Object[] variables = condition == null
				? new Object[] { dataset, "annee,nature,count(*) as n", "year(date_heure) as annee,nature", COUNTS_LIMIT }
				: new Object[] { dataset, "annee,nature,count(*) as n", "year(date_heure) as annee,nature",
						COUNTS_LIMIT, condition };
		String body = get(dataset, Instant.EPOCH, condition == null ? COUNTS : COUNTS_WHERE, variables,
				this::readText);
		try {
			RecordsPage<ConsolidatedCount> page = responses.nationalConsolidatedCounts(body);
			if (page.totalCount() > page.results().size()) {
				throw new OdreException(Reason.INVALID_RESPONSE,
						page.totalCount() + " groups but only " + page.results().size() + " returned");
			}
			return page.results();
		}
		catch (OdreException e) {
			throw failure(dataset, Instant.EPOCH, e);
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
		return get(rest, dataset, after, uriTemplate, variables, reader);
	}

	private <T> T get(RestClient client, String dataset, Instant after, String uriTemplate, Object[] variables,
			BodyReader<T> reader) {
		try {
			return client.get()
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
