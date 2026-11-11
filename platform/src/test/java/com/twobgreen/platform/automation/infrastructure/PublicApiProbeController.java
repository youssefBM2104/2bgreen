package com.twobgreen.platform.automation.infrastructure;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only endpoint under {@code /api/v1/public/**}: it tests the API-key chain alone, whatever the public
 * endpoints of U7 need behind it. Lives in src/test so it never ships.
 */
@RestController
class PublicApiProbeController {

	@GetMapping("/api/v1/public/test/caller")
	ApiKeyCaller caller(@AuthenticationPrincipal ApiKeyCaller caller) {
		return caller;
	}
}
