package com.twobgreen.gridintelligence.infrastructure.poller;

import java.time.Duration;
import java.time.Instant;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings {@code poller.*} ({@code application-poller.properties}).
 *
 * @param overlap how far before the last reading seen each poll starts again
 * @param firstStart where to start on an empty database: the oldest row of {@code eco2mix-national-tr}, also used
 *        for the regional dataset (same first day)
 * @param regionalOverlap same as {@code overlap} for the regional poll, wider because regions are filled in at
 *        different paces
 */
@ConfigurationProperties("poller")
record PollerProperties(Duration overlap, Instant firstStart, Duration regionalOverlap) {

	PollerProperties {
		if (overlap == null || overlap.isNegative()) {
			throw new IllegalArgumentException("poller.overlap is required and must not be negative");
		}
		if (regionalOverlap == null || regionalOverlap.isNegative()) {
			throw new IllegalArgumentException("poller.regional-overlap is required and must not be negative");
		}
		if (firstStart == null) {
			throw new IllegalArgumentException("poller.first-start is required");
		}
	}
}
