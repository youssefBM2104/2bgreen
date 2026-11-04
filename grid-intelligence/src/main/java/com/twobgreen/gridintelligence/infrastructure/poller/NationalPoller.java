package com.twobgreen.gridintelligence.infrastructure.poller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import com.twobgreen.gridintelligence.application.OdreException;
import com.twobgreen.gridintelligence.application.OdreException.Reason;
import com.twobgreen.gridintelligence.application.PollNationalReadings;

/**
 * Runs a poll every {@code poller.interval}, the first one at startup. A failure is logged and never thrown: the
 * cursor did not move, so the next tick retries the same range. No immediate retry, it would only spend the quota.
 */
class NationalPoller {

	private static final Logger log = LoggerFactory.getLogger(NationalPoller.class);

	private final PollNationalReadings polls;

	NationalPoller(PollNationalReadings polls) {
		this.polls = polls;
	}

	@Scheduled(initialDelayString = "${poller.initial-delay}", fixedDelayString = "${poller.interval}")
	void poll() {
		try {
			PollNationalReadings.Result result = polls.poll();
			log.info("Published {} national reading(s) observed after {}, newest seen {}",
					result.published(), result.after(), result.lastSeen());
		}
		catch (OdreException e) {
			if (e.reason() == Reason.QUOTA_EXCEEDED) {
				log.error("ODRÉ call quota used up, retrying at the next tick: {}", e.getMessage());
			}
			else {
				log.warn("ODRÉ could not be read ({}), retrying at the next tick: {}", e.reason(), e.getMessage());
			}
		}
		catch (RuntimeException e) {
			log.error("Poll failed, retrying at the next tick", e);
		}
	}
}
