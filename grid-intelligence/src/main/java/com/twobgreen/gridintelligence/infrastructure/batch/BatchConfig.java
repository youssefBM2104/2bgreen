package com.twobgreen.gridintelligence.infrastructure.batch;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.twobgreen.gridintelligence.application.BatchSnapshotRepository;
import com.twobgreen.gridintelligence.application.BatchSyncState;
import com.twobgreen.gridintelligence.application.ConsolidatedOdreGateway;
import com.twobgreen.gridintelligence.application.SyncNationalConsolidated;

/**
 * The batch, only started with the {@code batch} profile ({@code --spring.profiles.active=batch}). It needs no
 * Kafka: the batch layer never goes through the stream.
 */
@Configuration(proxyBeanMethods = false)
@Profile("batch")
@EnableScheduling
class BatchConfig {

	@Bean
	SyncNationalConsolidated syncNationalConsolidated(ConsolidatedOdreGateway odre, BatchSnapshotRepository snapshots,
			BatchSyncState state) {
		return new SyncNationalConsolidated(odre, snapshots, state);
	}

	@Bean
	NationalConsolidatedBatch nationalConsolidatedBatch(SyncNationalConsolidated sync) {
		return new NationalConsolidatedBatch(sync);
	}
}
