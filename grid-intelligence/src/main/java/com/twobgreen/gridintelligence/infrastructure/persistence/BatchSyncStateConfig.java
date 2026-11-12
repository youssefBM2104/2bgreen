package com.twobgreen.gridintelligence.infrastructure.persistence;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.twobgreen.gridintelligence.application.BatchSyncState;
import com.twobgreen.gridintelligence.domain.GridSnapshot;

/** One sync state per cons-def dataset, in the same table. The national one is the default (G3). */
@Configuration(proxyBeanMethods = false)
class BatchSyncStateConfig {

	@Bean
	@Primary
	BatchSyncState batchSyncState(JdbcClient jdbc) {
		return new JdbcBatchSyncState(jdbc, GridSnapshot.NATIONAL_SCOPE);
	}

	@Bean
	BatchSyncState regionalBatchSyncState(JdbcClient jdbc) {
		return new JdbcBatchSyncState(jdbc, JdbcBatchSyncState.REGIONAL);
	}
}
