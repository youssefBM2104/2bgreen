package com.twobgreen.platform.advisory.infrastructure;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.twobgreen.platform.advisory.application.GridReadings;
import com.twobgreen.platform.advisory.domain.GridReading;

/** The latest national reading for the citizen space. Path is provisional: F5 can reuse or replace it. */
@RestController
class GridReadingController {

	private final GridReadings readings;

	GridReadingController(GridReadings readings) {
		this.readings = readings;
	}

	@GetMapping("/api/v1/advisory/grid/latest")
	@PreAuthorize("hasRole('citizen')")
	GridReading latest() {
		return readings.latestNational().orElseThrow(NoGridReadingException::new);
	}
}
