package com.twobgreen.gridintelligence.infrastructure.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.twobgreen.gridintelligence.application.GetLatestSnapshot;
import com.twobgreen.gridintelligence.application.GetSnapshotRange;
import com.twobgreen.gridintelligence.application.SnapshotReader;

/**
 * The read API has no profile: it is served in every run mode.
 */
@Configuration(proxyBeanMethods = false)
class ApiConfig {

	@Bean
	GetLatestSnapshot getLatestSnapshot(SnapshotReader reader) {
		return new GetLatestSnapshot(reader);
	}

	@Bean
	GetSnapshotRange getSnapshotRange(SnapshotReader reader) {
		return new GetSnapshotRange(reader);
	}
}
