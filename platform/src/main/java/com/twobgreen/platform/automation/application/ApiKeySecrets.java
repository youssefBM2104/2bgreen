package com.twobgreen.platform.automation.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * What an API key looks like (U10). A key is {@code 2bg_} followed by 32 random bytes in URL-safe Base64 without
 * padding, 47 characters in all. Only its SHA-256 is stored: the key carries 256 random bits, so nobody can guess it
 * from its hash, and a slow hash such as bcrypt (made for short passwords) would only slow down every API call. A
 * fast hash also lets the filter find the key by its hash with the unique index. See ADR-005.
 */
final class ApiKeySecrets {

	static final String PREFIX = "2bg_";

	/** {@code 2bg_} and 4 random characters (24 bits), enough to tell keys apart in a list, useless to an attacker. */
	static final int SHOWN_LENGTH = PREFIX.length() + 4;

	private static final int RANDOM_BYTES = 32;

	static final int LENGTH = PREFIX.length() + 43;

	private ApiKeySecrets() {
	}

	static String generate(SecureRandom random) {
		byte[] bytes = new byte[RANDOM_BYTES];
		random.nextBytes(bytes);
		return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/** Cheap test done before any database access: a value of the wrong shape cannot be a key. */
	static boolean looksLikeAKey(String value) {
		return value != null && value.length() == LENGTH && value.startsWith(PREFIX);
	}

	static String shownPart(String key) {
		return key.substring(0, SHOWN_LENGTH);
	}

	/** SHA-256 of the key, 64 lowercase hex characters, as {@code automation_api_key.key_hash} expects. */
	static String hash(String key) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("every Java runtime has SHA-256", e);
		}
	}
}
