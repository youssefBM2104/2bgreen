package com.twobgreen.platform.automation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ApiKeySecretsTests {

	private final SecureRandom random = new SecureRandom();

	@Test
	void keyIsPrefixedAndCarries32RandomBytes() {
		String key = ApiKeySecrets.generate(random);

		assertThat(key).matches("2bg_[A-Za-z0-9_-]{43}").hasSize(ApiKeySecrets.LENGTH);
		assertThat(ApiKeySecrets.looksLikeAKey(key)).isTrue();
		assertThat(ApiKeySecrets.shownPart(key)).hasSize(8).startsWith("2bg_");
	}

	@Test
	void keysDoNotRepeat() {
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < 1000; i++) {
			assertThat(seen.add(ApiKeySecrets.generate(random))).isTrue();
		}
	}

	@Test
	void hashIsTheHexSha256TheTableExpects() {
		// Test vector of FIPS 180-2
		assertThat(ApiKeySecrets.hash("abc"))
				.isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
	}

	@Test
	void valuesOfTheWrongShapeAreNotKeys() {
		assertThat(ApiKeySecrets.looksLikeAKey(null)).isFalse();
		assertThat(ApiKeySecrets.looksLikeAKey("2bg_short")).isFalse();
		assertThat(ApiKeySecrets.looksLikeAKey("xyz_" + "A".repeat(43))).isFalse();
	}
}
