package com.twobgreen.gridintelligence.infrastructure.odre;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import com.twobgreen.gridintelligence.application.RegionalOdreGateway;
import com.twobgreen.gridintelligence.domain.GridSnapshot;

/**
 * {@link RegionalOdreGateway} on ODRÉ. All regions come in the same request: the query has no region filter, so a
 * poll costs the same number of calls whatever the number of regions (12 rows per period instead of 12 requests).
 * Rows are asked up to now, like the national ones.
 */
class RegionalRealtimeGateway implements RegionalOdreGateway {

	private final OdreClient client;

	private final Clock clock;

	RegionalRealtimeGateway(OdreClient client, Clock clock) {
		this.client = client;
		this.clock = clock;
	}

	@Override
	public List<GridSnapshot> regionalReadingsAfter(Instant after) {
		Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
		return client.regionalRealtimeBetween(after, now).stream()
				.map(RegionalRealtimeMapping::toSnapshot)
				.flatMap(Optional::stream)
				.toList();
	}
}
