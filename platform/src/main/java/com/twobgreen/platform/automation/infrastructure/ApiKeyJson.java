package com.twobgreen.platform.automation.infrastructure;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.twobgreen.platform.automation.application.ApiKeyUsage;
import com.twobgreen.platform.automation.application.ManageApiKeys;
import com.twobgreen.platform.automation.application.NewApiKey;
import com.twobgreen.platform.automation.domain.ApiKey;

/**
 * Bodies of {@code /api/v1/automation/api-keys}. The hash never leaves platform, the key itself only once, in
 * {@link Created}.
 */
final class ApiKeyJson {

	private ApiKeyJson() {
	}

	record Create(@NotBlank @Size(max = ManageApiKeys.MAX_NAME_LENGTH) String name) {
	}

	/**
	 * @param prefix start of the key, such as {@code 2bg_ab12}
	 * @param lastUsedAt null until the first accepted call
	 */
	record View(long id, String name, String prefix, Instant createdAt, Instant lastUsedAt, int callsToday,
			int dailyQuota) {

		static View of(ApiKeyUsage usage) {
			ApiKey key = usage.key();
			return new View(key.id(), key.name(), key.prefix(), key.createdAt(), key.lastUsedAt(),
					usage.callsToday(), usage.dailyQuota());
		}
	}

	/** {@link View} plus the key in full, answered once, to the creation. */
	record Created(long id, String name, String prefix, Instant createdAt, Instant lastUsedAt, int callsToday,
			int dailyQuota, String key) {

		static Created of(NewApiKey created) {
			View view = View.of(created.usage());
			return new Created(view.id(), view.name(), view.prefix(), view.createdAt(), view.lastUsedAt(),
					view.callsToday(), view.dailyQuota(), created.secret());
		}

		@Override
		public String toString() {
			return "Created[id=" + id + ", name=" + name + ", prefix=" + prefix + ", key=***]";
		}
	}
}
