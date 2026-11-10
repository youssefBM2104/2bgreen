package com.twobgreen.platform.advisory.infrastructure;

import java.math.BigDecimal;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings {@code advisory.traffic-light.*} ({@code application.properties}). Checked by
 * {@link com.twobgreen.platform.advisory.domain.TrafficLightPolicy}, so a wrong value stops the startup.
 */
@ConfigurationProperties("advisory.traffic-light")
record TrafficLightProperties(BigDecimal greenBelow, BigDecimal amberBelow, Duration maxAge) {
}
