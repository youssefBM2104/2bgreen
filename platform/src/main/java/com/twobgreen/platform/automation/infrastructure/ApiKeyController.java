package com.twobgreen.platform.automation.infrastructure;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.twobgreen.platform.automation.application.ManageApiKeys;

/**
 * The API keys of the DevOps user who calls (U10), managed from the browser with a Keycloak token. The owner is
 * always the {@code sub} claim of the token. A key cannot manage keys: these paths are not under
 * {@code /api/v1/public/**}.
 */
@RestController
@RequestMapping("/api/v1/automation/api-keys")
@PreAuthorize("hasRole('devops')")
class ApiKeyController {

	private final ManageApiKeys apiKeys;

	ApiKeyController(ManageApiKeys apiKeys) {
		this.apiKeys = apiKeys;
	}

	@GetMapping
	List<ApiKeyJson.View> list(@AuthenticationPrincipal Jwt token) {
		return apiKeys.listOf(token.getSubject()).stream().map(ApiKeyJson.View::of).toList();
	}

	/** The answer holds the key in full: no cache may keep it. */
	@PostMapping
	ResponseEntity<ApiKeyJson.Created> create(@AuthenticationPrincipal Jwt token,
			@Valid @RequestBody ApiKeyJson.Create body) {
		ApiKeyJson.Created created = ApiKeyJson.Created.of(apiKeys.create(token.getSubject(), body.name()));
		return ResponseEntity.created(URI.create("/api/v1/automation/api-keys/" + created.id()))
				.cacheControl(CacheControl.noStore())
				.body(created);
	}

	/** Revokes the key. It stays out of the list from now on. */
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void revoke(@AuthenticationPrincipal Jwt token, @PathVariable long id) {
		apiKeys.revoke(token.getSubject(), id);
	}
}
