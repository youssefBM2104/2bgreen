package com.twobgreen.platform.regional.infrastructure;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import com.twobgreen.platform.advisory.GridSnapshot;
import com.twobgreen.platform.regional.application.RegionalDashboard;
import com.twobgreen.platform.regional.domain.BalanceDirection;
import com.twobgreen.platform.regional.domain.EnergyBalance;

/**
 * Body of {@code GET /api/v1/regional/dashboard} (U11). A missing value is {@code null}, never 0. The regional CO2
 * always travels with its {@code co2Estimated} flag, so a screen cannot show it without knowing it is an estimate.
 *
 * @param maxAge ISO-8601 duration above which {@code regional.stale} is true
 * @param national the nation in the half hour of the regional reading, null when it has no data
 */
record RegionalDashboardJson(Region region, Reading regional, Reading national, String maxAge) {

	static RegionalDashboardJson of(RegionalDashboard dashboard) {
		Duration maxAge = dashboard.maxAge();
		return new RegionalDashboardJson(new Region(dashboard.regionCode(), dashboard.regionName()),
				Reading.of(dashboard.region(), dashboard.stale()),
				dashboard.national() == null ? null : Reading.of(dashboard.national(), null),
				maxAge.toString());
	}

	record Region(String code, String name) {
	}

	/**
	 * One reading with its balance. MW for the power figures, % for the rates, gCO2eq/kWh for the CO2.
	 *
	 * @param stale regional reading only (null for the national one, which is the same time on purpose)
	 * @param co2Estimated true when {@code co2Intensity} is an estimate (always for a region, G7)
	 */
	record Reading(
			Instant observedAt,
			Instant storedAt,
			String quality,
			Boolean stale,
			Integer consumption,
			Production production,
			Integer totalProduction,
			BigDecimal coverageRate,
			BalanceDirection balance,
			BigDecimal renewableShare,
			BigDecimal co2Intensity,
			boolean co2Estimated) {

		static Reading of(GridSnapshot snapshot, Boolean stale) {
			GridSnapshot.Production p = snapshot.production();
			EnergyBalance balance = EnergyBalance.of(snapshot.consumption(), p.nuclear(), p.thermal(), p.gas(),
					p.coal(), p.oil(), p.wind(), p.solar(), p.hydro(), p.bioenergy());
			return new Reading(snapshot.observedAt(), snapshot.storedAt(), snapshot.quality(), stale,
					balance.consumption(),
					new Production(balance.nuclear(), balance.thermal(), balance.wind(), balance.solar(),
							balance.hydro(), balance.bioenergy()),
					balance.totalProduction(), balance.coverageRate(), balance.direction(), balance.renewableShare(),
					snapshot.co2Intensity(), snapshot.estimated());
		}
	}

	/** MW by source. {@code thermal} is all fossil production, the only split regional data has. */
	record Production(Integer nuclear, Integer thermal, Integer wind, Integer solar, Integer hydro,
			Integer bioenergy) {
	}
}
