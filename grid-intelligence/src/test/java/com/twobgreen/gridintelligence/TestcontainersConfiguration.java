package com.twobgreen.gridintelligence;

import java.nio.file.Path;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	// Same pinned tag as docker-compose.yml
	private static final DockerImageName TIMESCALEDB = DockerImageName
			.parse("timescale/timescaledb:2.18.2-pg17")
			.asCompatibleSubstituteFor("postgres");

	// Maven runs from the module directory
	private static final Path INIT_SCRIPT = Path.of("..", "infra", "postgres", "init", "01-roles-and-schemas.sh")
			.toAbsolutePath().normalize();

	private static final String GRID_APP_PASSWORD = "grid_dev";

	// Same setup as docker-compose.yml: the real init script creates the roles, schemas and extension
	@Bean
	PostgreSQLContainer postgres() {
		return new PostgreSQLContainer(TIMESCALEDB)
				.withDatabaseName("2bgreen")
				.withUsername("postgres")
				.withPassword("postgres_dev")
				.withEnv("GRID_APP_PASSWORD", GRID_APP_PASSWORD)
				.withEnv("PLATFORM_APP_PASSWORD", "platform_dev")
				.withCopyFileToContainer(MountableFile.forHostPath(INIT_SCRIPT, 0755),
						"/docker-entrypoint-initdb.d/01-roles-and-schemas.sh");
	}

	// No @ServiceConnection on the container: it would override the user, and the app must connect as grid_app
	@Bean
	DynamicPropertyRegistrar datasource(PostgreSQLContainer postgres) {
		return registry -> {
			registry.add("spring.datasource.url", postgres::getJdbcUrl);
			registry.add("spring.datasource.username", () -> "grid_app");
			registry.add("spring.datasource.password", () -> GRID_APP_PASSWORD);
		};
	}

	@Bean
	@ServiceConnection
	KafkaContainer kafka() {
		return new KafkaContainer(DockerImageName.parse("apache/kafka:4.0.0"));
	}

}
