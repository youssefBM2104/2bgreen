package com.twobgreen.platform.regional.infrastructure;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.twobgreen.platform.regional.application.GetRegionalDashboard;

/**
 * The dashboard of the regional official (U11). The region is the {@code region_code} claim of the token, set in
 * Keycloak by an administrator: there is no parameter, so an official cannot ask for another region.
 */
@RestController
class RegionalDashboardController {

	static final String REGION_CLAIM = "region_code";

	private final GetRegionalDashboard getRegionalDashboard;

	RegionalDashboardController(GetRegionalDashboard getRegionalDashboard) {
		this.getRegionalDashboard = getRegionalDashboard;
	}

	@GetMapping("/api/v1/regional/dashboard")
	@PreAuthorize("hasRole('regional_official')")
	RegionalDashboardJson dashboard(@AuthenticationPrincipal Jwt token) {
		return RegionalDashboardJson.of(getRegionalDashboard.dashboard(token.getClaimAsString(REGION_CLAIM)));
	}
}
