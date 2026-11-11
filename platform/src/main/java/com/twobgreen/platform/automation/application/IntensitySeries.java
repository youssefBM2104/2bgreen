package com.twobgreen.platform.automation.application;

import java.time.Instant;
import java.util.List;

import com.twobgreen.platform.advisory.GridScope;
import com.twobgreen.platform.advisory.IntensityReading;

/**
 * The readings of a range, as asked.
 *
 * @param from start, included
 * @param to end, excluded
 * @param items ascending by {@code observedAt}, empty when nothing is stored in the range
 */
public record IntensitySeries(GridScope scope, Instant from, Instant to, List<IntensityReading> items) {
}
