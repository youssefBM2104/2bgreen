package com.twobgreen.platform.advisory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class GridScopeTests {

	@Test
	void nationalAndRegionAreTheK1Strings() {
		assertThat(GridScope.NATIONAL.isNational()).isTrue();
		assertThat(new GridScope("national")).isEqualTo(GridScope.NATIONAL);

		GridScope region = new GridScope("region:76");
		assertThat(region.isNational()).isFalse();
		assertThat(region.regionCode()).isEqualTo("76");
		assertThat(region).hasToString("region:76");
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "National", "france", "region:", "region:7", "region:076", "region:2A", "region:76 " })
	void anythingElseIsRefused(String value) {
		assertThatThrownBy(() -> new GridScope(value)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void nationalHasNoRegionCode() {
		assertThatThrownBy(GridScope.NATIONAL::regionCode).isInstanceOf(IllegalStateException.class);
	}
}
