package com.twobgreen.platform.regional.infrastructure;

import java.time.Clock;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.twobgreen.platform.advisory.GridSnapshots;
import com.twobgreen.platform.regional.application.ManageTargets;
import com.twobgreen.platform.regional.application.RecalculateTargets;
import com.twobgreen.platform.regional.application.RegionalIndicators;
import com.twobgreen.platform.regional.domain.TargetProgressRepository;
import com.twobgreen.platform.regional.domain.TargetRepository;

/**
 * Targets of the regional officials (U13): the use cases from the {@code regional.targets.*} settings, and the
 * scheduled recalculation.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(TargetConfig.TargetProperties.class)
class TargetConfig {

	/**
	 * @param minCoverage share of the half hours of a period that must have a complete reading, see
	 *        {@link RegionalIndicators}
	 * @param recalculationInterval time between two recalculations; each one replaces the row of the day
	 * @param recalculationInitialDelay time before the first one after startup
	 */
	@ConfigurationProperties("regional.targets")
	record TargetProperties(double minCoverage, Duration recalculationInterval, Duration recalculationInitialDelay) {
	}

	@Bean
	RegionalIndicators regionalIndicators(GridSnapshots grid, TargetProperties properties) {
		return new RegionalIndicators(grid, properties.minCoverage());
	}

	@Bean
	RecalculateTargets recalculateTargets(TargetRepository targets, TargetProgressRepository progress,
			RegionalIndicators indicators) {
		return new RecalculateTargets(targets, progress, indicators, Clock.systemUTC());
	}

	@Bean
	ManageTargets manageTargets(TargetRepository targets, TargetProgressRepository progress,
			RegionalIndicators indicators, RecalculateTargets recalculate, ApplicationEventPublisher events) {
		return new ManageTargets(targets, progress, indicators, recalculate, events, Clock.systemUTC());
	}

	@Bean
	TargetRecalculation targetRecalculation(RecalculateTargets recalculate) {
		return new TargetRecalculation(recalculate);
	}

	/** Runs the recalculation every {@code regional.targets.recalculation-interval}. Never throws. */
	static class TargetRecalculation {

		private static final Logger log = LoggerFactory.getLogger(TargetRecalculation.class);

		private final RecalculateTargets recalculate;

		TargetRecalculation(RecalculateTargets recalculate) {
			this.recalculate = recalculate;
		}

		@Scheduled(initialDelayString = "${regional.targets.recalculation-initial-delay}",
				fixedDelayString = "${regional.targets.recalculation-interval}")
		void run() {
			try {
				RecalculateTargets.Result result = recalculate.recalculateAll();
				log.info("Targets recalculated: {} recorded, {} without enough data or unreadable", result.recorded(),
						result.skipped());
			}
			catch (RuntimeException e) {
				log.error("Target recalculation failed, it runs again at the next tick", e);
			}
		}
	}
}
