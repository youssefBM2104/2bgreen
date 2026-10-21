package com.twobgreen.platform;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	// Same pinned tag as docker-compose.yml
	private static final DockerImageName TIMESCALEDB = DockerImageName
			.parse("timescale/timescaledb:2.18.2-pg17")
			.asCompatibleSubstituteFor("postgres");

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgres() {
		return new PostgreSQLContainer(TIMESCALEDB).withInitScript("platform-schema.sql");
	}

	@Bean
	@ServiceConnection
	KafkaContainer kafka() {
		return new KafkaContainer(DockerImageName.parse("apache/kafka:4.0.0"));
	}

}
