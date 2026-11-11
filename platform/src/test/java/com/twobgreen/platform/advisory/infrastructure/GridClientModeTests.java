package com.twobgreen.platform.advisory.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.twobgreen.platform.advisory.GridScope;
import com.twobgreen.platform.advisory.application.GridReadings;
import com.twobgreen.platform.advisory.domain.GridReading;

/** The switch between the two sources is configuration only, and never serves fake data by accident. */
class GridClientModeTests {

	private final ApplicationContextRunner context = new ApplicationContextRunner()
			.withUserConfiguration(GridClientConfig.class)
			.withPropertyValues(
					"grid.client.base-url=http://localhost:1",
					"grid.client.connect-timeout=1s",
					"grid.client.read-timeout=1s");

	@Test
	void restIsTheDefault() {
		context.run(app -> assertThat(app).hasSingleBean(GridReadings.class).hasSingleBean(RestGridReadings.class));
	}

	@Test
	void restCanBeSelected() {
		context.withPropertyValues("grid.client.mode=rest")
				.run(app -> assertThat(app).hasSingleBean(RestGridReadings.class));
	}

	@Test
	void stubCanBeSelectedAndServesTheContractExample() {
		context.withPropertyValues("grid.client.mode=stub").run(app -> {
			assertThat(app).hasSingleBean(StubGridReadings.class).doesNotHaveBean(RestGridReadings.class);
			assertThat(app.getBean(GridReadings.class).latestNational())
					.get()
					.extracting(GridReading::observedAt)
					.isEqualTo(Instant.parse("2026-10-20T14:45:00Z"));
		});
	}

	@Test
	void stubHasNoRegionAndARangeOnlyAroundItsReading() {
		StubGridReadings stub = StubGridReadings.fromContractExample();
		Instant observedAt = Instant.parse("2026-10-20T14:45:00Z");

		assertThat(stub.latest(new GridScope("region:76"))).isEmpty();
		assertThat(stub.between(GridScope.NATIONAL, observedAt, observedAt.plusSeconds(1))).hasSize(1);
		assertThat(stub.between(GridScope.NATIONAL, observedAt.minusSeconds(1), observedAt)).isEmpty();
		assertThat(stub.between(new GridScope("region:76"), observedAt, observedAt.plusSeconds(1))).isEmpty();
	}

	@Test
	void anUnknownModeLeavesNoSource() {
		context.withPropertyValues("grid.client.mode=stubb")
				.run(app -> assertThat(app).doesNotHaveBean(GridReadings.class));
	}
}
