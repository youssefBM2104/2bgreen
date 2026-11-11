package com.twobgreen.platform.automation.application;

import java.time.Instant;

/** Outcome of {@link CheckApiKey}. */
public sealed interface ApiKeyCheck {

	/** The call goes through and has been counted. */
	record Accepted(long keyId, String userId, Quota quota) implements ApiKeyCheck {
	}

	/**
	 * No key, a value that is not a key, an unknown key or a revoked one. The caller is not told which: an attacker
	 * learns nothing from the answer.
	 */
	record Refused() implements ApiKeyCheck {
	}

	/** A valid key that has used all its calls of the UTC day. */
	record QuotaExceeded(Quota quota) implements ApiKeyCheck {
	}

	/**
	 * @param limit calls per UTC day
	 * @param remaining calls left today, after this one
	 * @param resetAt next 00:00 UTC, when the counter starts again
	 */
	record Quota(int limit, int remaining, Instant resetAt) {
	}
}
