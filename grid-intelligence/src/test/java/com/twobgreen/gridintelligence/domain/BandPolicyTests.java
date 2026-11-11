package com.twobgreen.gridintelligence.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class BandPolicyTests {

	private final BandPolicy policy = new BandPolicy(BigDecimal.valueOf(20), BigDecimal.valueOf(40));

	@Test
	void aValueEqualToALimitBelongsToTheWorseBand() {
		assertThat(policy.bandOf(new BigDecimal("0"))).isEqualTo(Band.GREEN);
		assertThat(policy.bandOf(new BigDecimal("19.9"))).isEqualTo(Band.GREEN);
		assertThat(policy.bandOf(new BigDecimal("20"))).isEqualTo(Band.AMBER);
		assertThat(policy.bandOf(new BigDecimal("39.9"))).isEqualTo(Band.AMBER);
		assertThat(policy.bandOf(new BigDecimal("40"))).isEqualTo(Band.RED);
		assertThat(policy.bandOf(new BigDecimal("500"))).isEqualTo(Band.RED);
	}

	@Test
	void scaleDoesNotMatter() {
		assertThat(policy.bandOf(new BigDecimal("20.00"))).isEqualTo(Band.AMBER);
	}

	@Test
	void limitsMustBeGivenAndInOrder() {
		assertThatThrownBy(() -> new BandPolicy(null, BigDecimal.TEN)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new BandPolicy(BigDecimal.TEN, null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new BandPolicy(BigDecimal.TEN, BigDecimal.TEN))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new BandPolicy(BigDecimal.valueOf(40), BigDecimal.valueOf(20)))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
