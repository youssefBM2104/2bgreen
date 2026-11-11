package com.twobgreen.platform.automation.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.twobgreen.platform.advisory.CurrentIntensity;
import com.twobgreen.platform.advisory.IntensityReading;
import com.twobgreen.platform.automation.application.IntensitySeries;

/**
 * Bodies of the public intensity API (contract {@code docs/api/public-openapi.yaml}). Kept apart from the K2 bodies
 * of grid-intelligence: the public contract only shows CO2, and can stay stable while K2 changes.
 */
final class CarbonIntensityJson {

	private CarbonIntensityJson() {
	}

	/** {@code co2Intensity} is written even when null: a missing value is never 0, and never silently absent. */
	record Latest(
			String scope,
			Instant observedAt,
			BigDecimal co2Intensity,
			String quality,
			boolean estimated,
			String trafficLight,
			boolean stale,
			Instant storedAt) {

		static Latest of(CurrentIntensity current) {
			IntensityReading reading = current.reading();
			return new Latest(reading.scope().value(), reading.observedAt(), reading.co2Intensity(),
					reading.quality(), reading.estimated(), current.trafficLight(), current.stale(),
					reading.storedAt());
		}
	}

	record Point(Instant observedAt, BigDecimal co2Intensity, String quality, boolean estimated) {

		static Point of(IntensityReading reading) {
			return new Point(reading.observedAt(), reading.co2Intensity(), reading.quality(), reading.estimated());
		}
	}

	record Series(String scope, Instant from, Instant to, List<Point> items) {

		static Series of(IntensitySeries series) {
			return new Series(series.scope().value(), series.from(), series.to(),
					series.items().stream().map(Point::of).toList());
		}
	}
}
