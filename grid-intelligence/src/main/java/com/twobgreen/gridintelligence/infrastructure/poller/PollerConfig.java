package com.twobgreen.gridintelligence.infrastructure.poller;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.twobgreen.gridintelligence.application.OdreGateway;
import com.twobgreen.gridintelligence.application.PollNationalReadings;
import com.twobgreen.gridintelligence.application.SnapshotPublisher;
import com.twobgreen.gridintelligence.application.SpeedSnapshotRepository;

/**
 * The poller, only started with the {@code poller} profile ({@code --spring.profiles.active=poller,stream} runs the
 * whole speed layer in one process).
 */
@Configuration(proxyBeanMethods = false)
@Profile("poller")
@EnableScheduling
@EnableConfigurationProperties(PollerProperties.class)
class PollerConfig {

	@Bean
	PollNationalReadings pollNationalReadings(OdreGateway odre, SnapshotPublisher publisher,
			SpeedSnapshotRepository stored, PollerProperties properties) {
		return new PollNationalReadings(odre, publisher, stored, properties.overlap(), properties.firstStart());
	}

	@Bean
	NationalPoller nationalPoller(PollNationalReadings polls) {
		return new NationalPoller(polls);
	}
}
