package com.twobgreen.gridintelligence.infrastructure.messaging;

import java.time.Duration;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the K1 topics so that a fresh {@code docker compose up} works. Creation is idempotent, so this is
 * active on every profile. Settings are those of K1 section 2; one replica because the dev broker is a single node.
 */
@Configuration(proxyBeanMethods = false)
class KafkaTopicsConfig {

	private static final int PARTITIONS = 3;

	private static final String RETENTION_MS = String.valueOf(Duration.ofDays(7).toMillis());

	@Bean
	NewTopic snapshotReceivedTopic() {
		return topic(Topics.SNAPSHOT_RECEIVED);
	}

	@Bean
	NewTopic snapshotStoredTopic() {
		return topic(Topics.SNAPSHOT_STORED);
	}

	private static NewTopic topic(String name) {
		return TopicBuilder.name(name)
				.partitions(PARTITIONS)
				.replicas(1)
				.config(TopicConfig.RETENTION_MS_CONFIG, RETENTION_MS)
				.build();
	}
}
