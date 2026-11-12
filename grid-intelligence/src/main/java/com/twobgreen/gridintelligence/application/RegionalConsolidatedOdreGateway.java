package com.twobgreen.gridintelligence.application;

import java.util.List;

import com.twobgreen.gridintelligence.domain.GridSnapshot;

/**
 * Port to the consolidated and definitive regional history of ODRÉ ({@code eco2mix-regional-cons-def}), read by the
 * regional batch (G6). Same contract as {@link ConsolidatedOdreGateway}, for all regions at once.
 */
public interface RegionalConsolidatedOdreGateway {

	/**
	 * What the source holds per UTC year and quality, all regions together, in one cheap call. Oldest year first.
	 *
	 * @throws OdreException if ODRÉ cannot be read, or answers something that cannot be trusted
	 */
	List<YearQualityCount> regionalCounts();

	/**
	 * Every measured reading of a UTC year, all regions, at 30-min grain, without CO2 (the dataset has none): the
	 * phantom rows of the spring DST change are not returned. Each ({@code scope}, instant) appears once.
	 *
	 * @throws OdreException if ODRÉ cannot be read, or if one ({@code scope}, instant) comes back twice
	 */
	List<GridSnapshot> regionalReadings(int year);
}
