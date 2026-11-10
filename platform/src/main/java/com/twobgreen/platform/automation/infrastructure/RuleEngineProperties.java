package com.twobgreen.platform.automation.infrastructure;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings {@code automation.rules.*}.
 *
 * @param minInterval shortest time between two notifications of one rule or webhook
 * @param maxSnapshotAge older than this, a snapshot updates nothing a user would be told about
 */
@ConfigurationProperties("automation.rules")
record RuleEngineProperties(Duration minInterval, Duration maxSnapshotAge) {
}
