package com.twobgreen.platform.regional.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import com.twobgreen.platform.advisory.GridUnavailableException;
import com.twobgreen.platform.regional.TargetDeleted;
import com.twobgreen.platform.regional.domain.IndicatorSeries;
import com.twobgreen.platform.regional.domain.Regions;
import com.twobgreen.platform.regional.domain.Target;
import com.twobgreen.platform.regional.domain.TargetAssessment;
import com.twobgreen.platform.regional.domain.TargetIndicator;
import com.twobgreen.platform.regional.domain.TargetProgressRepository;
import com.twobgreen.platform.regional.domain.TargetRepository;

/**
 * Use case of the targets of a regional official (U13). Every method takes the user and the region of the caller's
 * token: a target of another user, or of another region, is never found, so an official can neither see nor create
 * a target outside their region.
 * <p>
 * The reference value is computed once, at creation, over the reference year (UTC calendar year): what the indicator
 * was that year. For a consumption drop it is the mean consumption of that year in MW, and the trajectory starts at
 * 0 %. Indicator and reference year cannot change afterwards: a different reference is a different target.
 */
public class ManageTargets {

	private static final Logger log = LoggerFactory.getLogger(ManageTargets.class);

	public static final int MAX_TARGETS_PER_USER = 10;

	/** The oldest year of the regional history at ODRÉ (cons-def starts in 2013). */
	public static final int OLDEST_REFERENCE_YEAR = 2013;

	private static final LocalDate LAST_DEADLINE = LocalDate.of(2100, 12, 31);

	/** Bounds of a target value, in the unit of each indicator. */
	private static final Map<TargetIndicator, BigDecimal[]> BOUNDS = Map.of(
			TargetIndicator.RENEWABLE_SHARE, new BigDecimal[] { BigDecimal.ZERO, BigDecimal.valueOf(100) },
			TargetIndicator.COVERAGE_RATE, new BigDecimal[] { BigDecimal.ZERO, BigDecimal.valueOf(1000) },
			TargetIndicator.CONSUMPTION_DROP, new BigDecimal[] { BigDecimal.valueOf(-100), BigDecimal.valueOf(100) },
			TargetIndicator.ESTIMATED_CO2, new BigDecimal[] { BigDecimal.ZERO, BigDecimal.valueOf(2000) });

	private final TargetRepository targets;

	private final TargetProgressRepository progress;

	private final RegionalIndicators indicators;

	private final RecalculateTargets recalculate;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	public ManageTargets(TargetRepository targets, TargetProgressRepository progress, RegionalIndicators indicators,
			RecalculateTargets recalculate, ApplicationEventPublisher events, Clock clock) {
		this.targets = targets;
		this.progress = progress;
		this.indicators = indicators;
		this.recalculate = recalculate;
		this.events = events;
		this.clock = clock;
	}

	/** The caller's targets in their region, with their history. */
	public List<TargetView> listOf(String userId, String regionCode) {
		requireRegion(regionCode);
		return ownTargets(userId, regionCode).stream().map(this::view).toList();
	}

	/**
	 * @throws InvalidTargetException if a field is missing or out of bounds, or the target equals the reference
	 * @throws TargetLimitReachedException if the user already has {@link #MAX_TARGETS_PER_USER} targets
	 * @throws ReferenceUnavailableException if the reference year has too little data in the region
	 * @throws GridUnavailableException if grid data cannot be read
	 */
	public TargetView create(String userId, String regionCode, TargetIndicator indicator, Integer referenceYear,
			BigDecimal targetValue, LocalDate deadline, BigDecimal tolerance) {
		requireRegion(regionCode);
		if (indicator == null) {
			throw new InvalidTargetException("indicator is required");
		}
		int lastYear = today().getYear() - 1;
		if (referenceYear == null || referenceYear < OLDEST_REFERENCE_YEAR || referenceYear > lastYear) {
			throw new InvalidTargetException(
					"referenceYear must be a past year, from " + OLDEST_REFERENCE_YEAR + " to " + lastYear);
		}
		requireValid(indicator, targetValue, deadline, tolerance);
		if (targets.findByUserIdOrderById(userId).size() >= MAX_TARGETS_PER_USER) {
			throw new TargetLimitReachedException();
		}
		BigDecimal reference = referenceValue(regionCode, indicator, referenceYear);
		Target draft = new Target(null, userId, regionCode, indicator, referenceYear, reference, targetValue, deadline,
				tolerance, Instant.now(clock).truncatedTo(ChronoUnit.MICROS));
		if (targetValue.compareTo(TargetAssessment.startValue(draft)) == 0) {
			throw new InvalidTargetException("targetValue equals the reference value: there is no way to go");
		}
		Target saved = targets.save(draft);
		recalculateNow(saved);
		return view(saved);
	}

	/**
	 * Changes what can change: the target value, the deadline, the tolerance. The trajectory still starts on the day
	 * the target was created.
	 *
	 * @throws TargetNotFoundException if the caller has no such target in their region
	 */
	public TargetView update(String userId, String regionCode, long id, BigDecimal targetValue, LocalDate deadline,
			BigDecimal tolerance) {
		requireRegion(regionCode);
		Target target = own(userId, regionCode, id);
		requireValid(target.indicator(), targetValue, deadline, tolerance);
		Target changed = new Target(target.id(), target.userId(), target.regionCode(), target.indicator(),
				target.referenceYear(), target.referenceValue(), targetValue, deadline, tolerance, target.createdAt());
		if (targetValue.compareTo(TargetAssessment.startValue(changed)) == 0) {
			throw new InvalidTargetException("targetValue equals the reference value: there is no way to go");
		}
		Target saved = targets.save(changed);
		recalculateNow(saved);
		return view(saved);
	}

	/**
	 * Deletes the target and its history (the database cascades), and tells the other modules.
	 *
	 * @throws TargetNotFoundException if the caller has no such target in their region
	 */
	public void delete(String userId, String regionCode, long id) {
		requireRegion(regionCode);
		Target target = own(userId, regionCode, id);
		targets.delete(target);
		events.publishEvent(new TargetDeleted(target.id()));
	}

	private BigDecimal referenceValue(String regionCode, TargetIndicator indicator, int year) {
		Instant from = LocalDate.of(year, 1, 1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
		Instant to = LocalDate.of(year + 1, 1, 1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
		IndicatorSeries series = indicators.over(regionCode, from, to)
				.orElseThrow(() -> new ReferenceUnavailableException(year));
		return (indicator == TargetIndicator.CONSUMPTION_DROP ? series.meanConsumption()
				: series.value(indicator, null)).orElseThrow(() -> new ReferenceUnavailableException(year));
	}

	/** Best effort: a failure here only delays the first value to the next scheduled run. */
	private void recalculateNow(Target target) {
		try {
			recalculate.recalculate(target);
		}
		catch (GridUnavailableException e) {
			log.warn("Target {} stored, its first value waits for the next recalculation: {}", target.id(),
					e.getMessage());
		}
	}

	private TargetView view(Target target) {
		List<TargetView.Day> history = progress.findByTargetIdOrderByDay(target.id()).stream()
				.map(row -> new TargetView.Day(row.day(), TargetAssessment.of(target, row.value(), row.day())))
				.toList();
		return new TargetView(target, history.isEmpty() ? null : history.getLast(), history);
	}

	private List<Target> ownTargets(String userId, String regionCode) {
		return targets.findByUserIdOrderById(userId).stream()
				.filter(target -> target.regionCode().equals(regionCode))
				.toList();
	}

	private Target own(String userId, String regionCode, long id) {
		return targets.findByIdAndUserId(id, userId)
				.filter(target -> target.regionCode().equals(regionCode))
				.orElseThrow(TargetNotFoundException::new);
	}

	private void requireValid(TargetIndicator indicator, BigDecimal targetValue, LocalDate deadline,
			BigDecimal tolerance) {
		BigDecimal[] bounds = BOUNDS.get(indicator);
		if (targetValue == null || targetValue.compareTo(bounds[0]) < 0 || targetValue.compareTo(bounds[1]) > 0) {
			throw new InvalidTargetException(
					"targetValue must be between " + bounds[0] + " and " + bounds[1] + " for " + indicator);
		}
		if (deadline == null || !deadline.isAfter(today()) || deadline.isAfter(LAST_DEADLINE)) {
			throw new InvalidTargetException("deadline must be after today and before " + LAST_DEADLINE);
		}
		if (tolerance != null && (tolerance.signum() <= 0 || tolerance.compareTo(BigDecimal.valueOf(100)) > 0)) {
			throw new InvalidTargetException("tolerance must be above 0 and at most 100, or absent");
		}
	}

	private static void requireRegion(String regionCode) {
		if (Regions.nameOf(regionCode).isEmpty()) {
			throw new RegionNotAssignedException();
		}
	}

	private LocalDate today() {
		return LocalDate.ofInstant(clock.instant(), RecalculateTargets.PARIS);
	}
}
