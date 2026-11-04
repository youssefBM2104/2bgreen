package com.twobgreen.gridintelligence.infrastructure.odre;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import com.twobgreen.gridintelligence.application.OdreGateway;
import com.twobgreen.gridintelligence.domain.GridSnapshot;

/**
 * {@link OdreGateway} on ODRÉ: the client fetches the rows, the mapping translates them. Errors of the client
 * ({@code OdreException}) go through unchanged.
 * <p>
 * A measured reading is never in the future, so rows are only asked up to now: the forecast tail (until the end of
 * tomorrow, about 120 rows) never leaves ODRÉ, and a regular poll costs one call.
 */
class NationalRealtimeGateway implements OdreGateway {

	private final OdreClient client;

	private final Clock clock;

	NationalRealtimeGateway(OdreClient client, Clock clock) {
		this.client = client;
		this.clock = clock;
	}

	@Override
	public List<GridSnapshot> nationalReadingsAfter(Instant after) {
		Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
		return client.nationalRealtimeBetween(after, now).stream()
				.map(NationalRealtimeMapping::toSnapshot)
				.flatMap(Optional::stream)
				.toList();
	}
}
