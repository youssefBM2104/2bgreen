package com.twobgreen.platform.regional.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * The readings of a region over a period, added up to compute the indicators of a target (U13). Ratios are taken on
 * the totals of the period (energy over the period), not as an average of half-hourly ratios, so a night with little
 * production weighs what it produced.
 * <p>
 * A reading counts only when its balance is complete (consumption and the six sources, see {@link EnergyBalance}): a
 * reading with a missing source would bias every ratio. The CO2 average is weighted by production, over the readings
 * that have a CO2 value.
 */
public final class IndicatorSeries {

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	private static final int SCALE = 2;

	private long periods;

	private long consumption;

	private long production;

	private long renewable;

	private BigDecimal co2TimesProduction = BigDecimal.ZERO;

	private long co2Production;

	/** Adds one reading; an incomplete one is ignored. */
	public void add(EnergyBalance balance, BigDecimal co2Intensity) {
		if (balance.consumption() == null || balance.totalProduction() == null) {
			return;
		}
		periods++;
		consumption += balance.consumption();
		production += balance.totalProduction();
		renewable += balance.wind() + balance.solar() + balance.hydro() + balance.bioenergy();
		if (co2Intensity != null) {
			co2TimesProduction = co2TimesProduction.add(co2Intensity.multiply(BigDecimal.valueOf(balance.totalProduction())));
			co2Production += balance.totalProduction();
		}
	}

	/** Complete readings added. */
	public long periods() {
		return periods;
	}

	/** Average consumption over the period, MW; empty without readings. */
	public Optional<BigDecimal> meanConsumption() {
		return periods == 0 ? Optional.empty() : Optional.of(divide(BigDecimal.valueOf(consumption), periods));
	}

	/**
	 * The value of an indicator over the period, in its unit (%, or gCO2eq/kWh for the CO2).
	 *
	 * @param referenceConsumption mean consumption of the reference year, MW; only used by
	 *        {@link TargetIndicator#CONSUMPTION_DROP}
	 * @return empty when the period has nothing to compute it from
	 */
	public Optional<BigDecimal> value(TargetIndicator indicator, BigDecimal referenceConsumption) {
		return switch (indicator) {
			case RENEWABLE_SHARE -> production == 0 ? Optional.empty()
					: Optional.of(divide(BigDecimal.valueOf(renewable).multiply(HUNDRED), production));
			case COVERAGE_RATE -> consumption == 0 ? Optional.empty()
					: Optional.of(divide(BigDecimal.valueOf(production).multiply(HUNDRED), consumption));
			case CONSUMPTION_DROP -> referenceConsumption == null || referenceConsumption.signum() <= 0
					? Optional.empty()
					: meanConsumption().map(mean -> referenceConsumption.subtract(mean).multiply(HUNDRED)
							.divide(referenceConsumption, SCALE, RoundingMode.HALF_UP));
			case ESTIMATED_CO2 -> co2Production == 0 ? Optional.empty()
					: Optional.of(divide(co2TimesProduction, co2Production));
		};
	}

	private static BigDecimal divide(BigDecimal value, long by) {
		return value.divide(BigDecimal.valueOf(by), SCALE, RoundingMode.HALF_UP);
	}
}
