package com.twobgreen.platform.regional.application;

import java.time.LocalDate;
import java.util.List;

import com.twobgreen.platform.regional.domain.Target;
import com.twobgreen.platform.regional.domain.TargetAssessment;

/**
 * A target with its progress, as the official sees it (U13).
 *
 * @param current the latest day of the history, null until the first recalculation found enough data
 * @param history one entry per day, oldest first, each assessed against the trajectory of today's target
 */
public record TargetView(Target target, Day current, List<Day> history) {

	public record Day(LocalDate day, TargetAssessment assessment) {
	}
}
