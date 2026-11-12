package com.twobgreen.platform.regional.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.twobgreen.platform.advisory.GridUnavailableException;
import com.twobgreen.platform.regional.domain.IndicatorSeries;
import com.twobgreen.platform.regional.domain.Target;
import com.twobgreen.platform.regional.domain.TargetAssessment;
import com.twobgreen.platform.regional.domain.TargetIndicator;
import com.twobgreen.platform.regional.domain.TargetProgressRepository;
import com.twobgreen.platform.regional.domain.TargetRepository;

/**
 * The recalculation of the targets (U13): for each target, the value of its indicator over the last 12 months (a
 * rolling year smooths the seasons out: a winter is not compared with a summer) and its status against the
 * trajectory, stored as the row of today in the progress history.
 * <ul>
 * <li>One row per target and day (Paris time): running again the same day replaces it. So the run can be frequent,
 * and the row of the day follows the data as it arrives, whenever the regional batch (G6) or the speed layer brings
 * some: platform is not told when the batch ran.</li>
 * <li>The 12 months end at the start of today in Paris: the current day is incomplete.</li>
 * <li>The readings are read once per region, whatever the number of targets in it.</li>
 * <li>Too little data over the 12 months: no row that day, the history keeps a hole rather than a wrong value.</li>
 * </ul>
 */
public class RecalculateTargets {

	private static final Logger log = LoggerFactory.getLogger(RecalculateTargets.class);

	static final ZoneId PARIS = ZoneId.of("Europe/Paris");

	private static final Duration ROLLING_YEAR = Duration.ofDays(365);

	/**
	 * What a run did, for the logs.
	 *
	 * @param recorded targets that got their row of the day
	 * @param skipped targets of a region without enough data, or whose data could not be read
	 */
	public record Result(int recorded, int skipped) {
	}

	private final TargetRepository targets;

	private final TargetProgressRepository progress;

	private final RegionalIndicators indicators;

	private final Clock clock;

	public RecalculateTargets(TargetRepository targets, TargetProgressRepository progress,
			RegionalIndicators indicators, Clock clock) {
		this.targets = targets;
		this.progress = progress;
		this.indicators = indicators;
		this.clock = clock;
	}

	/** Every target of every region. A region that fails does not stop the others. */
	public synchronized Result recalculateAll() {
		Map<String, List<Target>> byRegion = targets.findAll().stream()
				.collect(Collectors.groupingBy(Target::regionCode));
		int recorded = 0;
		int skipped = 0;
		for (Map.Entry<String, List<Target>> region : byRegion.entrySet()) {
			Optional<IndicatorSeries> series;
			try {
				series = rollingYear(region.getKey());
			}
			catch (GridUnavailableException e) {
				log.warn("Targets of region {} not recalculated, grid data unavailable: {}", region.getKey(),
						e.getMessage());
				skipped += region.getValue().size();
				continue;
			}
			for (Target target : region.getValue()) {
				if (series.isPresent() && record(target, series.get())) {
					recorded++;
				}
				else {
					skipped++;
				}
			}
		}
		return new Result(recorded, skipped);
	}

	/**
	 * One target, right after it was created or changed, so its page shows a value at once.
	 *
	 * @return whether a row was recorded
	 * @throws GridUnavailableException if grid data cannot be read
	 */
	public boolean recalculate(Target target) {
		return rollingYear(target.regionCode()).map(series -> record(target, series)).orElse(false);
	}

	private Optional<IndicatorSeries> rollingYear(String regionCode) {
		Instant to = today().atStartOfDay(PARIS).toInstant();
		return indicators.over(regionCode, to.minus(ROLLING_YEAR), to);
	}

	private boolean record(Target target, IndicatorSeries series) {
		BigDecimal reference = target.indicator() == TargetIndicator.CONSUMPTION_DROP ? target.referenceValue() : null;
		Optional<BigDecimal> value = series.value(target.indicator(), reference);
		if (value.isEmpty()) {
			return false;
		}
		LocalDate day = today();
		TargetAssessment assessment = TargetAssessment.of(target, value.get(), day);
		progress.record(target.id(), day, value.get(), assessment.status().name());
		return true;
	}

	private LocalDate today() {
		return LocalDate.ofInstant(clock.instant(), PARIS);
	}
}
