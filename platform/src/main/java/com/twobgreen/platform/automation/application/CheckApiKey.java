package com.twobgreen.platform.automation.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import com.twobgreen.platform.automation.domain.ApiKey;
import com.twobgreen.platform.automation.domain.ApiKeyRepository;

/**
 * Use case of the authentication filter of {@code /api/v1/public/**} (U10): decides whether a call made with a key
 * goes through, and counts it. Nothing is cached, so a key revoked a moment ago is refused at the next call.
 * <p>
 * The quota is per key and per UTC day. A refused call is not counted, so a client that keeps calling after the
 * limit does not push its counter further.
 */
public class CheckApiKey {

	private final ApiKeyRepository keys;

	private final Clock clock;

	private final int dailyQuota;

	public CheckApiKey(ApiKeyRepository keys, Clock clock, int dailyQuota) {
		if (dailyQuota < 1) {
			throw new IllegalArgumentException("daily quota must be at least 1");
		}
		this.keys = keys;
		this.clock = clock;
		this.dailyQuota = dailyQuota;
	}

	/**
	 * @param presented the value of the request header, possibly null
	 */
	public ApiKeyCheck check(String presented) {
		if (!ApiKeySecrets.looksLikeAKey(presented)) {
			return new ApiKeyCheck.Refused();
		}
		Optional<ApiKey> found = keys.findByKeyHash(ApiKeySecrets.hash(presented));
		if (found.isEmpty() || found.get().revokedAt() != null) {
			return new ApiKeyCheck.Refused();
		}
		ApiKey key = found.get();
		Instant now = clock.instant();
		LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
		Instant resetAt = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
		Optional<Integer> calls = keys.countCall(key.id(), today, dailyQuota);
		if (calls.isEmpty()) {
			return new ApiKeyCheck.QuotaExceeded(new ApiKeyCheck.Quota(dailyQuota, 0, resetAt));
		}
		keys.markUsed(key.id(), now.truncatedTo(ChronoUnit.MICROS));
		return new ApiKeyCheck.Accepted(key.id(), key.userId(),
				new ApiKeyCheck.Quota(dailyQuota, dailyQuota - calls.get(), resetAt));
	}
}
