package com.twobgreen.gridintelligence.infrastructure.odre;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.twobgreen.gridintelligence.application.OdreException;
import com.twobgreen.gridintelligence.application.OdreException.Reason;
import com.twobgreen.gridintelligence.application.RegionalConsolidatedOdreGateway;
import com.twobgreen.gridintelligence.application.YearQualityCount;
import com.twobgreen.gridintelligence.domain.GridSnapshot;

/** {@link RegionalConsolidatedOdreGateway} on ODRÉ, same shape as {@link NationalConsolidatedGateway}. */
class RegionalConsolidatedGateway implements RegionalConsolidatedOdreGateway {

	private final OdreClient client;

	/** INSEE codes to load, empty for every region. */
	private final List<String> regions;

	RegionalConsolidatedGateway(OdreClient client) {
		this(client, List.of());
	}

	/**
	 * @param regions the regions to load, two-digit INSEE codes; empty means all of them
	 * @throws IllegalArgumentException if a code is not two digits, so a wrong setting stops the app at startup
	 */
	RegionalConsolidatedGateway(OdreClient client, List<String> regions) {
		OdreClient.regionCondition(regions);
		this.client = client;
		this.regions = List.copyOf(regions);
	}

	@Override
	public List<YearQualityCount> regionalCounts() {
		return client.regionalConsolidatedCounts(regions).stream()
				.map(count -> new YearQualityCount(count.year(),
						NationalConsolidatedMapping.quality(count.nature(), "in the regional count of " + count.year()),
						count.rows()))
				.sorted(Comparator.comparingInt(YearQualityCount::year).thenComparing(YearQualityCount::quality))
				.toList();
	}

	@Override
	public List<GridSnapshot> regionalReadings(int year) {
		Instant from = Instant.parse(year + "-01-01T00:00:00Z");
		Instant to = from.atOffset(ZoneOffset.UTC).plusYears(1).toInstant();
		List<GridSnapshot> readings = client.regionalConsolidatedBetween(from, to, regions).stream()
				.map(RegionalConsolidatedMapping::toSnapshot)
				.flatMap(Optional::stream)
				.toList();
		Set<String> seen = new HashSet<>();
		for (GridSnapshot reading : readings) {
			if (!seen.add(reading.scope() + "@" + reading.observedAt())) {
				throw new OdreException(Reason.INVALID_RESPONSE, "two rows for " + reading.scope() + " at "
						+ reading.observedAt() + " in " + year + " after dropping known anomalies");
			}
		}
		return readings;
	}
}
