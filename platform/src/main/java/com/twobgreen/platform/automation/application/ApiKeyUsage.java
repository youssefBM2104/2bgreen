package com.twobgreen.platform.automation.application;

import com.twobgreen.platform.automation.domain.ApiKey;

/**
 * A key as its owner sees it in the list.
 *
 * @param callsToday calls accepted since 00:00 UTC
 * @param dailyQuota calls accepted per UTC day
 */
public record ApiKeyUsage(ApiKey key, int callsToday, int dailyQuota) {
}
