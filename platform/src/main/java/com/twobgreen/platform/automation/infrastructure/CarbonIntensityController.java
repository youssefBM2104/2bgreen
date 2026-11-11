package com.twobgreen.platform.automation.infrastructure;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.twobgreen.platform.automation.application.GetCarbonIntensity;

/**
 * Public API of the DevOps (U7, contract {@code docs/api/public-openapi.yaml}). It sits under
 * {@code /api/v1/public/**}, so the API-key chain of {@link ApiKeyConfig} has already checked the key and counted the
 * call before a method here runs: a JWT never reaches it.
 * <p>
 * Parameters are read as text and checked by the use case, so a missing or malformed one is the documented
 * {@code INVALID_PARAMETER}.
 */
@RestController
@RequestMapping(path = "/api/v1/public/intensity", produces = MediaType.APPLICATION_JSON_VALUE)
class CarbonIntensityController {

	private final GetCarbonIntensity getCarbonIntensity;

	CarbonIntensityController(GetCarbonIntensity getCarbonIntensity) {
		this.getCarbonIntensity = getCarbonIntensity;
	}

	@GetMapping("/latest")
	CarbonIntensityJson.Latest latest(@RequestParam(defaultValue = "national") String scope) {
		return getCarbonIntensity.latest(scope).map(CarbonIntensityJson.Latest::of)
				.orElseThrow(NoIntensityException::new);
	}

	@GetMapping
	CarbonIntensityJson.Series between(@RequestParam(defaultValue = "national") String scope,
			@RequestParam(required = false) String from, @RequestParam(required = false) String to) {
		return CarbonIntensityJson.Series.of(getCarbonIntensity.between(scope, from, to));
	}

	/** Nothing stored yet for the scope. Normal on a fresh start, or for a region without data. */
	static class NoIntensityException extends RuntimeException {

		NoIntensityException() {
			super("No reading is stored yet for this scope");
		}
	}
}
