package com.twobgreen.gridintelligence.infrastructure.poller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import com.twobgreen.gridintelligence.application.OdreException;
import com.twobgreen.gridintelligence.application.OdreException.Reason;
import com.twobgreen.gridintelligence.application.PollRegionalReadings;

/**
 * Runs a regional poll every {@code poller.regional-interval} (hourly: ODRÉ refreshes the regional dataset hourly,
 * so a faster rhythm would only spend the quota). Failures are logged, never thrown, like {@link NationalPoller}.
 */
class RegionalPoller {

	private static final Logger log = LoggerFactory.getLogger(RegionalPoller.class);

	private final PollRegionalReadings polls;

	RegionalPoller(PollRegionalReadings polls) {
		this.polls = polls;
	}

	@Scheduled(initialDelayString = "${poller.initial-delay}", fixedDelayString = "${poller.regional-interval}")
	void poll() {
		try {
			PollRegionalReadings.Result result = polls.poll();
			log.info("Published {} regional reading(s) observed after {}, newest seen {}",
					result.published(), result.after(), result.lastSeen());
		}
		catch (OdreException e) {
			if (e.reason() == Reason.QUOTA_EXCEEDED) {
				log.error("ODRÉ call quota used up, retrying at the next regional tick: {}", e.getMessage());
			}
			else {
				log.warn("ODRÉ regional data could not be read ({}), retrying at the next tick: {}", e.reason(),
						e.getMessage());
			}
		}
		catch (RuntimeException e) {
			log.error("Regional poll failed, retrying at the next tick", e);
		}
	}
}
