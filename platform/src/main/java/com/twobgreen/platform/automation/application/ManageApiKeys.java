package com.twobgreen.platform.automation.application;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.transaction.support.TransactionOperations;

import com.twobgreen.platform.automation.domain.ApiKey;
import com.twobgreen.platform.automation.domain.ApiKeyRepository;

/**
 * Use case of the DevOps space (U10): a user creates, lists and revokes their own API keys. Every method takes the
 * user id of the caller and only ever reaches the keys of that user.
 * <p>
 * A revoked key stays in the table 30 days for audit (inventory row 6) but is no longer listed, nor counted in the
 * limit, nor accepted by {@link CheckApiKey}.
 */
public class ManageApiKeys {

	public static final int MAX_ACTIVE_KEYS_PER_USER = 5;

	public static final int MAX_NAME_LENGTH = 60;

	private final ApiKeyRepository keys;

	private final TransactionOperations transaction;

	private final SecureRandom random;

	private final Clock clock;

	private final int dailyQuota;

	public ManageApiKeys(ApiKeyRepository keys, TransactionOperations transaction, SecureRandom random, Clock clock,
			int dailyQuota) {
		this.keys = keys;
		this.transaction = transaction;
		this.random = random;
		this.clock = clock;
		this.dailyQuota = dailyQuota;
	}

	/** The active keys of the user, oldest first, with their calls of the current UTC day. */
	public List<ApiKeyUsage> listOf(String userId) {
		LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
		return keys.findByUserIdAndRevokedAtIsNullOrderById(userId).stream()
				.map(key -> new ApiKeyUsage(key, keys.callsOn(key.id(), today).orElse(0), dailyQuota))
				.toList();
	}

	/**
	 * Creates a key. The returned {@link NewApiKey} is the only place where the key appears in full: it is not
	 * stored, and cannot be shown again.
	 *
	 * @throws ApiKeyLimitReachedException if the user already has {@link #MAX_ACTIVE_KEYS_PER_USER} active keys
	 */
	public NewApiKey create(String userId, String name) {
		String cleanName = requireValid(name);
		String secret = ApiKeySecrets.generate(random);
		ApiKey stored = transaction.execute(status -> {
			// Two requests of one user wait for each other here, so both cannot count 4 and both insert
			keys.lockKeysOfUser(userId);
			if (keys.countByUserIdAndRevokedAtIsNull(userId) >= MAX_ACTIVE_KEYS_PER_USER) {
				throw new ApiKeyLimitReachedException();
			}
			return keys.save(new ApiKey(null, userId, cleanName, ApiKeySecrets.shownPart(secret),
					ApiKeySecrets.hash(secret), now(), null, null));
		});
		return new NewApiKey(new ApiKeyUsage(stored, 0, dailyQuota), secret);
	}

	/**
	 * Revokes a key at once: the next call with it is refused. Revoking a key twice keeps the first date.
	 *
	 * @throws ApiKeyNotFoundException if the user has no such key
	 */
	public void revoke(String userId, long id) {
		ApiKey key = keys.findByIdAndUserId(id, userId).orElseThrow(() -> new ApiKeyNotFoundException(id));
		if (key.revokedAt() == null) {
			keys.save(new ApiKey(key.id(), key.userId(), key.name(), key.prefix(), key.keyHash(), key.createdAt(),
					now(), key.lastUsedAt()));
		}
	}

	/** The database keeps microseconds: the key returned is the key a later read gives. */
	private Instant now() {
		return clock.instant().truncatedTo(ChronoUnit.MICROS);
	}

	private static String requireValid(String name) {
		String clean = name == null ? "" : name.strip();
		if (clean.isEmpty() || clean.length() > MAX_NAME_LENGTH) {
			throw new IllegalArgumentException("name must have 1 to " + MAX_NAME_LENGTH + " characters");
		}
		return clean;
	}
}
