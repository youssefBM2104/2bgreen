package com.twobgreen.platform.regional.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * What a scope consumes against what it produces, at one reading (U11). Every figure is null when it cannot be
 * computed: a missing value is unknown, never 0.
 * <ul>
 * <li>{@code thermal} is the fossil aggregate. Regional data gives it as one figure; for the nation it is gas + coal +
 * oil, so that both show the same sources.</li>
 * <li>The total needs all six sources: with one missing it would be too small, and every rate built on it wrong.
 * Pumping and batteries are not production, as in the CO2 estimate (G7).</li>
 * <li>Rates are percentages with one decimal.</li>
 * </ul>
 *
 * @param consumption MW
 * @param thermal MW, fossil production
 * @param totalProduction MW, sum of the six sources
 * @param coverageRate production / consumption, %; above 100 the scope produces more than it consumes
 * @param direction from the same comparison
 * @param renewableShare (wind + solar + hydro + bioenergy) / production, %. Hydro includes the turbines of pumped
 *        storage, which regional data cannot separate.
 */
public record EnergyBalance(
		Integer consumption,
		Integer nuclear,
		Integer thermal,
		Integer wind,
		Integer solar,
		Integer hydro,
		Integer bioenergy,
		Integer totalProduction,
		BigDecimal coverageRate,
		BalanceDirection direction,
		BigDecimal renewableShare) {

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	/**
	 * @param thermal the fossil aggregate when the source gives it, else null
	 * @param gas national split, used only when {@code thermal} is null
	 */
	public static EnergyBalance of(Integer consumption, Integer nuclear, Integer thermal, Integer gas, Integer coal,
			Integer oil, Integer wind, Integer solar, Integer hydro, Integer bioenergy) {
		Integer fossil = thermal != null ? thermal : sum(gas, coal, oil);
		Integer total = sum(nuclear, fossil, wind, solar, hydro, bioenergy);
		Integer renewable = sum(wind, solar, hydro, bioenergy);
		BigDecimal coverage = percent(total, consumption);
		BalanceDirection direction = total == null || consumption == null ? null
				: switch (Integer.signum(Integer.compare(total, consumption))) {
					case 1 -> BalanceDirection.EXPORTING;
					case -1 -> BalanceDirection.IMPORTING;
					default -> BalanceDirection.BALANCED;
				};
		return new EnergyBalance(consumption, nuclear, fossil, wind, solar, hydro, bioenergy, total, coverage,
				direction, percent(renewable, total));
	}

	/** Null as soon as one value is unknown. */
	private static Integer sum(Integer... values) {
		int total = 0;
		for (Integer value : values) {
			if (value == null) {
				return null;
			}
			total += value;
		}
		return total;
	}

	/** {@code part / whole} in %, null when either is unknown or the whole is not positive. */
	private static BigDecimal percent(Integer part, Integer whole) {
		if (part == null || whole == null || whole <= 0) {
			return null;
		}
		return BigDecimal.valueOf(part).multiply(HUNDRED).divide(BigDecimal.valueOf(whole), 1, RoundingMode.HALF_UP);
	}
}
