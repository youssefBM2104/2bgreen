package com.twobgreen.platform;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Test-only endpoints to exercise role checks; lives in src/test so it never ships. */
@RestController
class RoleProbeController {

	@GetMapping("/test/citizen")
	@PreAuthorize("hasRole('citizen')")
	String citizen() {
		return "ok";
	}

	@GetMapping("/test/devops")
	@PreAuthorize("hasRole('devops')")
	String devops() {
		return "ok";
	}

	@GetMapping("/test/elu")
	@PreAuthorize("hasRole('regional_official')")
	String elu() {
		return "ok";
	}

}
