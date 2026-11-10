package com.twobgreen.gridintelligence.infrastructure.odre;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.twobgreen.gridintelligence.application.ConsolidatedOdreGateway;
import com.twobgreen.gridintelligence.application.OdreException;
import com.twobgreen.gridintelligence.application.OdreException.Reason;
import com.twobgreen.gridintelligence.application.YearQualityCount;
import com.twobgreen.gridintelligence.domain.GridSnapshot;

/**
 * {@link ConsolidatedOdreGateway} on ODRÉ: the client fetches, the mapping translates. Errors of the client
 * ({@code OdreException}) go through unchanged.
 */
class NationalConsolidatedGateway implements ConsolidatedOdreGateway {

	private final OdreClient client;

	NationalConsolidatedGateway(OdreClient client) {
		this.client = client;
	}

	@Override
	public List<YearQualityCount> nationalCounts() {
		return client.nationalConsolidatedCounts().stream()
				.map(count -> new YearQualityCount(count.year(),
						NationalConsolidatedMapping.quality(count.nature(), "in the count of " + count.year()),
						count.rows()))
				.sorted(java.util.Comparator.comparingInt(YearQualityCount::year)
						.thenComparing(YearQualityCount::quality))
				.toList();
	}

	@Override
	public List<GridSnapshot> nationalReadings(int year) {
		Instant from = Instant.parse(year + "-01-01T00:00:00Z");
		Instant to = from.atOffset(ZoneOffset.UTC).plusYears(1).toInstant();
		List<GridSnapshot> readings = client.nationalConsolidatedBetween(from, to).stream()
				.map(NationalConsolidatedMapping::toSnapshot)
				.flatMap(Optional::stream)
				.toList();
		Set<Instant> seen = new HashSet<>();
		for (GridSnapshot reading : readings) {
			if (!seen.add(reading.observedAt())) {
				throw new OdreException(Reason.INVALID_RESPONSE,
						"two rows for " + reading.observedAt() + " in " + year + " after dropping known anomalies");
			}
		}
		return readings;
	}
}
