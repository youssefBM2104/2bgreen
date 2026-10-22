package com.twobgreen.gridintelligence.infrastructure.stream;

import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse;
import org.apache.kafka.streams.kstream.KStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.config.StreamsBuilderFactoryBeanConfigurer;

import com.twobgreen.gridintelligence.application.SpeedSnapshotRepository;
import com.twobgreen.gridintelligence.application.StoreSnapshot;

/**
 * The speed layer stream, only started with the {@code stream} profile. The application id
 * ({@code application-stream.properties}) is also the consumer group, {@code grid-stream} in contract K1.
 */
@Configuration(proxyBeanMethods = false)
@Profile("stream")
@EnableKafkaStreams
class SnapshotStreamConfig {

	private static final Logger log = LoggerFactory.getLogger(SnapshotStreamConfig.class);

	@Bean
	StoreSnapshot storeSnapshot(SpeedSnapshotRepository repository) {
		return new StoreSnapshot(repository);
	}

	@Bean
	SnapshotMessages snapshotMessages() {
		return new SnapshotMessages();
	}

	@Bean
	KStream<String, String> snapshotStream(StreamsBuilder builder, StoreSnapshot store, SnapshotMessages messages) {
		return SnapshotTopology.build(builder, store, messages);
	}

	/**
	 * A failure here is the database being unavailable (bad messages are dropped before). Replace the thread: it
	 * restarts from the last committed offset, so nothing is lost and the upsert makes the replay harmless.
	 */
	@Bean
	StreamsBuilderFactoryBeanConfigurer replaceThreadOnFailure() {
		return factoryBean -> factoryBean.setStreamsUncaughtExceptionHandler(exception -> {
			log.error("Stream thread failed, replacing it", exception);
			return StreamThreadExceptionResponse.REPLACE_THREAD;
		});
	}
}
