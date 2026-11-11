package com.twobgreen.platform.regional.infrastructure;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings {@code regional.dashboard.*} ({@code application.properties}). Checked by
 * {@link com.twobgreen.platform.regional.application.GetRegionalDashboard}, so a wrong value stops the startup.
 *
 * @param maxAge age above which a regional reading is shown as {@code stale}
 */
@ConfigurationProperties("regional.dashboard")
record RegionalDashboardProperties(Duration maxAge) {
}
