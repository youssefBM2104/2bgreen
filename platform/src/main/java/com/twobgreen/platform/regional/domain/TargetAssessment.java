package com.twobgreen.platform.regional.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Where a target stands on a day (U13), from the value of its indicator that day.
 * <ul>
 * <li><b>Trajectory.</b> A straight line from the start value, on the day the target was created, to the target
 * value, on the deadline. After the deadline the expected value is the target.</li>
 * <li><b>Direction.</b> Whether the indicator must go up or down comes from the target itself (above or below the
 * start), so a CO2 target set lower than its reference is a target to go down, with no rule per indicator.</li>
 * <li><b>Status.</b> {@code AHEAD} when the value is better than expected by more than the tolerance, {@code LATE}
 * when worse by more than the tolerance, {@code ON_TRACK} in between. No tolerance means 0.</li>
 * <li><b>Progress.</b> The share of the way from the start to the target, from 0 to 100 %: a value past the target is
 * {@code achieved}, at 100 %, never more; a value that went the wrong way is 0 %.</li>
 * </ul>
 *
 * @param expected the value on the trajectory that day
 * @param percent share of the way covered, 0 to 100, one decimal
 */
public record TargetAssessment(BigDecimal value, BigDecimal expected, TargetStatus status, BigDecimal percent,
		boolean achieved) {

	private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	/**
	 * @param value the indicator that day, in the unit of the indicator
	 */
	public static TargetAssessment of(Target target, BigDecimal value, LocalDate day) {
		BigDecimal start = startValue(target);
		BigDecimal goal = target.targetValue();
		int direction = goal.compareTo(start);
		BigDecimal expected = expected(target, start, day);
		BigDecimal tolerance = target.tolerance() == null ? BigDecimal.ZERO : target.tolerance();
		BigDecimal gap = value.subtract(expected).multiply(BigDecimal.valueOf(direction));
		TargetStatus status = gap.compareTo(tolerance) > 0 ? TargetStatus.AHEAD
				: gap.compareTo(tolerance.negate()) < 0 ? TargetStatus.LATE : TargetStatus.ON_TRACK;
		boolean achieved = direction != 0 && value.subtract(goal).multiply(BigDecimal.valueOf(direction)).signum() >= 0;
		BigDecimal percent = achieved ? HUNDRED.setScale(1) : percent(start, goal, value);
		return new TargetAssessment(value, expected, status, percent, achieved);
	}

	/**
	 * Where the trajectory starts: the reference value, except for a consumption drop, which starts at 0 % (the
	 * reference year compared with itself; its reference value is the mean consumption in MW).
	 */
	public static BigDecimal startValue(Target target) {
		return target.indicator() == TargetIndicator.CONSUMPTION_DROP ? BigDecimal.ZERO : target.referenceValue();
	}

	/** The day the trajectory starts: the creation of the target, in Paris time. */
	public static LocalDate startDay(Target target) {
		return target.createdAt().atZone(PARIS).toLocalDate();
	}

	private static BigDecimal expected(Target target, BigDecimal start, LocalDate day) {
		long total = ChronoUnit.DAYS.between(startDay(target), target.deadline());
		long done = ChronoUnit.DAYS.between(startDay(target), day);
		if (total <= 0 || done >= total) {
			return target.targetValue().setScale(2, RoundingMode.HALF_UP);
		}
		if (done <= 0) {
			return start.setScale(2, RoundingMode.HALF_UP);
		}
		return start.add(target.targetValue().subtract(start).multiply(BigDecimal.valueOf(done))
				.divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP)).setScale(2, RoundingMode.HALF_UP);
	}

	private static BigDecimal percent(BigDecimal start, BigDecimal goal, BigDecimal value) {
		BigDecimal way = goal.subtract(start);
		if (way.signum() == 0) {
			return HUNDRED.setScale(1);
		}
		BigDecimal share = value.subtract(start).multiply(HUNDRED).divide(way, 1, RoundingMode.HALF_UP);
		return share.max(BigDecimal.ZERO.setScale(1)).min(HUNDRED.setScale(1));
	}
}
