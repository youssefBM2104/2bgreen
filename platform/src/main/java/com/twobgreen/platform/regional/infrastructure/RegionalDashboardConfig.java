package com.twobgreen.platform.regional.infrastructure;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.twobgreen.platform.advisory.GridSnapshots;
import com.twobgreen.platform.regional.application.GetRegionalDashboard;

/** Creates the use case of the regional dashboard (U11) from the {@code regional.dashboard.*} settings. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RegionalDashboardProperties.class)
class RegionalDashboardConfig {

	@Bean
	GetRegionalDashboard getRegionalDashboard(GridSnapshots grid, RegionalDashboardProperties properties) {
		return new GetRegionalDashboard(grid, Clock.systemUTC(), properties.maxAge());
	}
}
