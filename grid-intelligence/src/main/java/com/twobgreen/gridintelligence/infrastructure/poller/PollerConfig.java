package com.twobgreen.gridintelligence.infrastructure.poller;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.twobgreen.gridintelligence.application.OdreGateway;
import com.twobgreen.gridintelligence.application.PollNationalReadings;
import com.twobgreen.gridintelligence.application.PollRegionalReadings;
import com.twobgreen.gridintelligence.application.RegionalOdreGateway;
import com.twobgreen.gridintelligence.application.SnapshotPublisher;
import com.twobgreen.gridintelligence.application.SpeedSnapshotRepository;
import com.twobgreen.gridintelligence.domain.RegionalCo2Estimator;

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

	@Bean
	PollRegionalReadings pollRegionalReadings(RegionalOdreGateway odre, SnapshotPublisher publisher,
			SpeedSnapshotRepository stored, PollerProperties properties) {
		return new PollRegionalReadings(odre, publisher, stored, new RegionalCo2Estimator(),
				properties.regionalOverlap(), properties.firstStart());
	}

	@Bean
	RegionalPoller regionalPoller(PollRegionalReadings polls) {
		return new RegionalPoller(polls);
	}
}
