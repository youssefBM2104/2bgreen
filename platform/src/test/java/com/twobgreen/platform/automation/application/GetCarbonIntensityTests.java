package com.twobgreen.platform.automation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.twobgreen.platform.advisory.CurrentIntensity;
import com.twobgreen.platform.advisory.GridIntensity;
import com.twobgreen.platform.advisory.GridScope;
import com.twobgreen.platform.advisory.IntensityReading;

class GetCarbonIntensityTests {

	private final List<String> calls = new ArrayList<>();

	private final GetCarbonIntensity useCase = new GetCarbonIntensity(new GridIntensity() {

		@Override
		public Optional<CurrentIntensity> latest(GridScope scope) {
			calls.add("latest " + scope);
			return Optional.empty();
		}

		@Override
		public List<IntensityReading> between(GridScope scope, Instant from, Instant to) {
			calls.add(scope + " " + from + " " + to);
			return List.of();
		}
	});

	@Test
	void exactlySevenDaysIsAccepted() {
		IntensitySeries series = useCase.between("national", "2026-11-03T00:00:00Z", "2026-11-10T00:00:00Z");

		assertThat(series.scope()).isEqualTo(GridScope.NATIONAL);
		assertThat(series.items()).isEmpty();
		assertThat(calls).containsExactly("national 2026-11-03T00:00:00Z 2026-11-10T00:00:00Z");
	}

	@Test
	void oneSecondMoreIsRefused() {
		assertThatThrownBy(() -> useCase.between("national", "2026-11-03T00:00:00Z", "2026-11-10T00:00:01Z"))
				.isInstanceOf(InvalidIntensityRequestException.class)
				.hasMessageContaining("7 days");
		assertThat(calls).isEmpty();
	}

	@Test
	void anEmptyOrReversedRangeIsRefused() {
		assertThatThrownBy(() -> useCase.between("national", "2026-11-03T00:00:00Z", "2026-11-03T00:00:00Z"))
				.hasMessageContaining("before");
		assertThatThrownBy(() -> useCase.between("national", "2026-11-04T00:00:00Z", "2026-11-03T00:00:00Z"))
				.hasMessageContaining("before");
	}

	@Test
	void missingOrMalformedDatesNameTheParameter() {
		assertThatThrownBy(() -> useCase.between("national", null, "2026-11-03T00:00:00Z"))
				.hasMessage("'from' is required.");
		assertThatThrownBy(() -> useCase.between("national", "2026-11-03T00:00:00Z", " "))
				.hasMessage("'to' is required.");
		assertThatThrownBy(() -> useCase.between("national", "2026-11-03", "2026-11-04T00:00:00Z"))
				.hasMessageContaining("'from' must be an ISO-8601 date-time");
	}

	@Test
	void scopeIsCheckedBeforeAnything() {
		assertThatThrownBy(() -> useCase.latest("france")).isInstanceOf(InvalidIntensityRequestException.class);
		assertThatThrownBy(() -> useCase.between(null, "x", "y")).hasMessageContaining("'scope'");
		assertThat(calls).isEmpty();
	}

	@Test
	void regionIsPassedOn() {
		useCase.latest("region:76");

		assertThat(calls).containsExactly("latest region:76");
	}
}
