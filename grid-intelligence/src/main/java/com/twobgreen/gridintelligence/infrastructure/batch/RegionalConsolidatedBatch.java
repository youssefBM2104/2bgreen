package com.twobgreen.gridintelligence.infrastructure.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import com.twobgreen.gridintelligence.application.OdreException;
import com.twobgreen.gridintelligence.application.OdreException.Reason;
import com.twobgreen.gridintelligence.application.SyncRegionalConsolidated;

/**
 * Runs the regional resynchronisation (G6) on the same rhythm as the national one, see
 * {@link NationalConsolidatedBatch}: a failure is logged and never thrown, the next run resumes with the year that
 * failed.
 */
class RegionalConsolidatedBatch {

	private static final Logger log = LoggerFactory.getLogger(RegionalConsolidatedBatch.class);

	private final SyncRegionalConsolidated sync;

	RegionalConsolidatedBatch(SyncRegionalConsolidated sync) {
		this.sync = sync;
	}

	@Scheduled(initialDelayString = "${batch.initial-delay}", fixedDelayString = "${batch.interval}")
	void run() {
		try {
			SyncRegionalConsolidated.Result result = sync.sync();
			if (result.yearsLoaded().isEmpty()) {
				log.info("Regional batch: {} year(s) at ODRÉ, nothing changed since the last run",
						result.yearsChecked());
			}
			else {
				log.info("Regional batch: {} year(s) at ODRÉ, loaded {}, {} row(s) written", result.yearsChecked(),
						result.yearsLoaded(), result.rowsWritten());
			}
		}
		catch (OdreException e) {
			if (e.reason() == Reason.QUOTA_EXCEEDED) {
				log.error("ODRÉ call quota used up, the regional batch retries at the next run: {}", e.getMessage());
			}
			else {
				log.warn("ODRÉ could not be read ({}), the regional batch retries at the next run: {}", e.reason(),
						e.getMessage());
			}
		}
		catch (RuntimeException e) {
			log.error("Regional batch failed, it retries at the next run", e);
		}
	}
}
