package com.twobgreen.platform.automation.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings {@code automation.api-keys.*}.
 *
 * @param dailyQuota calls accepted per key and per UTC day
 */
@ConfigurationProperties("automation.api-keys")
record ApiKeyProperties(int dailyQuota) {
}
