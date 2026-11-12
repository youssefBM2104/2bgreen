package com.twobgreen.platform.regional.infrastructure;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.twobgreen.platform.regional.application.InvalidTargetException;
import com.twobgreen.platform.regional.application.ManageTargets;
import com.twobgreen.platform.regional.domain.TargetIndicator;

/**
 * The targets of the regional official (U13). The user is the {@code sub} of the token and the region its
 * {@code region_code} claim, never a value of the request: an official only reaches their own targets, in their own
 * region.
 */
@RestController
@RequestMapping("/api/v1/regional/targets")
@PreAuthorize("hasRole('regional_official')")
class TargetController {

	private final ManageTargets manageTargets;

	TargetController(ManageTargets manageTargets) {
		this.manageTargets = manageTargets;
	}

	@GetMapping
	List<TargetJson> list(@AuthenticationPrincipal Jwt token) {
		return manageTargets.listOf(token.getSubject(), region(token)).stream().map(TargetJson::of).toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	TargetJson create(@AuthenticationPrincipal Jwt token, @RequestBody NewTarget body) {
		return TargetJson.of(manageTargets.create(token.getSubject(), region(token), indicator(body.indicator()),
				body.referenceYear(), body.targetValue(), date(body.deadline()), body.tolerance()));
	}

	@PutMapping("/{id}")
	TargetJson update(@AuthenticationPrincipal Jwt token, @PathVariable long id, @RequestBody TargetChanges body) {
		return TargetJson.of(manageTargets.update(token.getSubject(), region(token), id, body.targetValue(),
				date(body.deadline()), body.tolerance()));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal Jwt token, @PathVariable long id) {
		manageTargets.delete(token.getSubject(), region(token), id);
	}

	/**
	 * @param indicator {@code RENEWABLE_SHARE}, {@code COVERAGE_RATE}, {@code CONSUMPTION_DROP} or
	 *        {@code ESTIMATED_CO2}
	 * @param deadline {@code yyyy-MM-dd}
	 * @param tolerance points around the trajectory counted as on track; absent turns the pace alert (U16) off
	 */
	record NewTarget(String indicator, Integer referenceYear, BigDecimal targetValue, String deadline,
			BigDecimal tolerance) {
	}

	/** What can change after creation. All three are given each time; an absent tolerance removes it. */
	record TargetChanges(BigDecimal targetValue, String deadline, BigDecimal tolerance) {
	}

	private static String region(Jwt token) {
		return token.getClaimAsString(RegionalDashboardController.REGION_CLAIM);
	}

	// Read as text, so a wrong value is the documented INVALID_TARGET and not a framework error without code
	private static TargetIndicator indicator(String value) {
		if (value == null) {
			return null;
		}
		try {
			return TargetIndicator.valueOf(value);
		}
		catch (IllegalArgumentException e) {
			throw new InvalidTargetException("unknown indicator");
		}
	}

	private static LocalDate date(String value) {
		if (value == null) {
			return null;
		}
		try {
			return LocalDate.parse(value);
		}
		catch (DateTimeParseException e) {
			throw new InvalidTargetException("deadline must be a date, yyyy-MM-dd");
		}
	}
}
