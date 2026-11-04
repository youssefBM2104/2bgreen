package com.twobgreen.gridintelligence.infrastructure.odre;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
 * (one row per period); regional ones have one row per region and period and will need another cursor.
 */
class OdreClient {

	static final String NATIONAL_REALTIME = "eco2mix-national-tr";

	private static final Logger log = LoggerFactory.getLogger(OdreClient.class);

	private static final String RECORDS = "/catalog/datasets/{dataset}/records"
			+ "?where={where}&order_by=date_heure&limit={limit}";

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
		Objects.requireNonNull(after, "after");
		Objects.requireNonNull(before, "before");
		List<NationalRealtimeRecord> rows = new ArrayList<>();
		// Nothing can match: no call, the quota is spared
		if (!before.isAfter(after)) {
			return rows;
		}
		Instant cursor = after;
		int pages = 0;
		while (true) {
			RecordsPage<NationalRealtimeRecord> page = nationalRealtimePage(cursor, before);
			pages++;
			rows.addAll(page.results());
			if (page.results().size() < pageSize || page.results().size() >= page.totalCount()) {
				log.debug("ODRÉ {}: {} rows between {} and {} in {} page(s)", NATIONAL_REALTIME, rows.size(), after,
						before, pages);
				return rows;
			}
			Instant last = page.results().getLast().dateHeure();
			// A server ignoring the filter or the order would make the loop endless
			if (!last.isAfter(cursor)) {
				throw failure(NATIONAL_REALTIME, cursor,
						new OdreException(Reason.INVALID_RESPONSE, "page does not move past " + cursor));
			}
			cursor = last;
		}
	}

	private RecordsPage<NationalRealtimeRecord> nationalRealtimePage(Instant after, Instant before) {
		String body = fetch(NATIONAL_REALTIME, after, before);
		try {
			return responses.nationalRealtime(body);
		}
		catch (OdreException e) {
			throw failure(NATIONAL_REALTIME, after, e);
		}
	}

	/** One request. Only the dataset, the cursor and the status are logged: never the body. */
	private String fetch(String dataset, Instant after, Instant before) {
		String where = "date_heure > date'" + after + "' and date_heure < date'" + before + "'";
		try {
			return rest.get()
					.uri(RECORDS, dataset, where, pageSize)
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
						return StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
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
