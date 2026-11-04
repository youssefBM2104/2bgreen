package com.twobgreen.gridintelligence.infrastructure.poller;

import java.time.Duration;
import java.time.Instant;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings {@code poller.*} ({@code application-poller.properties}).
 *
 * @param overlap how far before the last reading seen each poll starts again
 * @param firstStart where to start on an empty database: the oldest row of {@code eco2mix-national-tr}
 */
@ConfigurationProperties("poller")
record PollerProperties(Duration overlap, Instant firstStart) {

	PollerProperties {
		if (overlap == null || overlap.isNegative()) {
			throw new IllegalArgumentException("poller.overlap is required and must not be negative");
		}
		if (firstStart == null) {
			throw new IllegalArgumentException("poller.first-start is required");
		}
	}
}
