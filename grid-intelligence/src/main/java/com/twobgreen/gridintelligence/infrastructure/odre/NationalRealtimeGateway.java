package com.twobgreen.gridintelligence.infrastructure.odre;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.twobgreen.gridintelligence.application.OdreGateway;
import com.twobgreen.gridintelligence.domain.GridSnapshot;

/**
 * {@link OdreGateway} on ODRÉ: the client fetches the rows, the mapping translates them. Errors of the client
 * ({@code OdreException}) go through unchanged.
 */
class NationalRealtimeGateway implements OdreGateway {

	private final OdreClient client;

	NationalRealtimeGateway(OdreClient client) {
		this.client = client;
	}

	@Override
	public List<GridSnapshot> nationalReadingsAfter(Instant after) {
		return client.nationalRealtimeAfter(after).stream()
				.map(NationalRealtimeMapping::toSnapshot)
				.flatMap(Optional::stream)
				.toList();
	}
}
