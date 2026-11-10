package com.twobgreen.gridintelligence.infrastructure.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import com.twobgreen.gridintelligence.application.OdreException;
import com.twobgreen.gridintelligence.application.OdreException.Reason;
import com.twobgreen.gridintelligence.application.SyncNationalConsolidated;

/**
 * Runs the resynchronisation every {@code batch.interval}, the first one at startup (on an empty table that is the
 * initial load of the whole history). A failure is logged and never thrown: the years already loaded stay recorded,
 * so the next run resumes where this one stopped. No immediate retry, it would only spend the quota.
 */
class NationalConsolidatedBatch {

	private static final Logger log = LoggerFactory.getLogger(NationalConsolidatedBatch.class);

	private final SyncNationalConsolidated sync;

	NationalConsolidatedBatch(SyncNationalConsolidated sync) {
		this.sync = sync;
	}

	@Scheduled(initialDelayString = "${batch.initial-delay}", fixedDelayString = "${batch.interval}")
	void run() {
		try {
			SyncNationalConsolidated.Result result = sync.sync();
			if (result.yearsLoaded().isEmpty()) {
				log.info("Batch: {} year(s) at ODRÉ, nothing changed since the last run", result.yearsChecked());
			}
			else {
				log.info("Batch: {} year(s) at ODRÉ, loaded {}, {} row(s) written", result.yearsChecked(),
						result.yearsLoaded(), result.rowsWritten());
			}
		}
		catch (OdreException e) {
			if (e.reason() == Reason.QUOTA_EXCEEDED) {
				log.error("ODRÉ call quota used up, the batch retries at the next run: {}", e.getMessage());
			}
			else {
				log.warn("ODRÉ could not be read ({}), the batch retries at the next run: {}", e.reason(),
						e.getMessage());
			}
		}
		catch (RuntimeException e) {
			log.error("Batch failed, it retries at the next run", e);
		}
	}
}
