package com.twobgreen.gridintelligence.infrastructure.stream;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings {@code stream.band.*}, gCO2eq/kWh. They must equal {@code advisory.traffic-light.*} of {@code platform}
 * (F5): the two apps cannot share code, so the limits are written twice.
 *
 * @param greenBelow below this the band is green
 * @param amberBelow below this the band is amber, from this value it is red
 */
@ConfigurationProperties("stream.band")
record BandProperties(BigDecimal greenBelow, BigDecimal amberBelow) {
}
