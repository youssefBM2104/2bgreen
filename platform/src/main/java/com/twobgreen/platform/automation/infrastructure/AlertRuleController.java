package com.twobgreen.platform.automation.infrastructure;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

import com.twobgreen.platform.automation.application.ManageAlertRules;

/**
 * The clean-grid alerts of the citizen who calls (U2). The owner is always the {@code sub} claim of the token, never
 * a value of the request, so nobody can name another user.
 */
@RestController
@RequestMapping("/api/v1/automation/alerts")
@PreAuthorize("hasRole('citizen')")
class AlertRuleController {

	private final ManageAlertRules alertRules;

	AlertRuleController(ManageAlertRules alertRules) {
		this.alertRules = alertRules;
	}

	@GetMapping
	List<AlertRuleJson.View> list(@AuthenticationPrincipal Jwt token) {
		return alertRules.listOf(token.getSubject()).stream().map(AlertRuleJson.View::of).toList();
	}

	@PostMapping
	ResponseEntity<AlertRuleJson.View> create(@AuthenticationPrincipal Jwt token,
			@Valid @RequestBody AlertRuleJson.Create body) {
		AlertRuleJson.View created = AlertRuleJson.View.of(alertRules.create(token.getSubject(), body.threshold(),
				body.active() == null || body.active()));
		return ResponseEntity.created(URI.create("/api/v1/automation/alerts/" + created.id())).body(created);
	}

	@PutMapping("/{id}")
	AlertRuleJson.View replace(@AuthenticationPrincipal Jwt token, @PathVariable long id,
			@Valid @RequestBody AlertRuleJson.Replace body) {
		return AlertRuleJson.View.of(alertRules.update(token.getSubject(), id, body.threshold(), body.active()));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal Jwt token, @PathVariable long id) {
		alertRules.delete(token.getSubject(), id);
	}
}
