package com.twobgreen.platform.regional.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.twobgreen.platform.regional.application.TargetView;
import com.twobgreen.platform.regional.domain.Target;
import com.twobgreen.platform.regional.domain.TargetAssessment;
import com.twobgreen.platform.regional.domain.TargetIndicator;
import com.twobgreen.platform.regional.domain.TargetStatus;

/**
 * A target in the API (U13). Values are in the unit of the indicator ({@code unit}). For a consumption drop the
 * trajectory starts at 0 % and {@code referenceConsumption} gives the mean consumption of the reference year, MW.
 *
 * @param referenceValue where the trajectory starts, in {@code unit}
 * @param current the latest day of the history, null until the first value
 */
record TargetJson(long id, TargetIndicator indicator, String unit, int referenceYear, BigDecimal referenceValue,
		BigDecimal referenceConsumption, BigDecimal targetValue, LocalDate deadline, BigDecimal tolerance,
		Instant createdAt, Current current, List<Point> history) {

	static TargetJson of(TargetView view) {
		Target target = view.target();
		boolean drop = target.indicator() == TargetIndicator.CONSUMPTION_DROP;
		return new TargetJson(target.id(), target.indicator(),
				target.indicator() == TargetIndicator.ESTIMATED_CO2 ? "gCO2eq/kWh" : "%", target.referenceYear(),
				TargetAssessment.startValue(target), drop ? target.referenceValue() : null, target.targetValue(),
				target.deadline(), target.tolerance(), target.createdAt(),
				view.current() == null ? null : Current.of(view.current()),
				view.history().stream().map(Point::of).toList());
	}

	/**
	 * Where the target stands now.
	 *
	 * @param day the day of the value (Paris); the value covers the 12 months before it
	 * @param percent share of the way from the reference to the target, 0 to 100, never more
	 * @param achieved the target is reached: show it as such, the percent is then 100
	 */
	record Current(LocalDate day, BigDecimal value, BigDecimal expected, TargetStatus status, BigDecimal percent,
			boolean achieved) {

		static Current of(TargetView.Day day) {
			TargetAssessment a = day.assessment();
			return new Current(day.day(), a.value(), a.expected(), a.status(), a.percent(), a.achieved());
		}
	}

	/** One day of the history, for the progress chart: the value and the trajectory that day. */
	record Point(LocalDate day, BigDecimal value, BigDecimal expected, TargetStatus status) {

		static Point of(TargetView.Day day) {
			TargetAssessment a = day.assessment();
			return new Point(day.day(), a.value(), a.expected(), a.status());
		}
	}
}
