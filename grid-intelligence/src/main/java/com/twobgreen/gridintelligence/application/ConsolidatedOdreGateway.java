package com.twobgreen.gridintelligence.application;

import java.util.List;

import com.twobgreen.gridintelligence.domain.GridSnapshot;

/**
 * Port to the consolidated and definitive national history of ODRÉ ({@code eco2mix-national-cons-def}), read by the
 * batch. Like {@link OdreGateway}, the adapter keeps every ODRÉ name and DTO to itself.
 */
public interface ConsolidatedOdreGateway {

	/**
	 * What the source holds per UTC year and quality, in one cheap call. Oldest year first.
	 *
	 * @throws OdreException if ODRÉ cannot be read, or answers something that cannot be trusted
	 */
	List<YearQualityCount> nationalCounts();

	/**
	 * Every measured reading of a UTC year, at 30-min grain: the empty placeholders and the phantom rows of the
	 * spring DST change are not returned. Each instant appears once, in no particular order.
	 *
	 * @throws OdreException if ODRÉ cannot be read, or if one instant comes back twice (a new anomaly of the source:
	 *         storing either row would be a guess)
	 */
	List<GridSnapshot> nationalReadings(int year);
}
