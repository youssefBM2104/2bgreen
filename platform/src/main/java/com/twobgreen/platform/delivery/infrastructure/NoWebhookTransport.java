package com.twobgreen.platform.delivery.infrastructure;

import org.springframework.stereotype.Component;

import com.twobgreen.platform.delivery.WebhookCall;
import com.twobgreen.platform.delivery.application.WebhookTransport;

/**
 * Sends nothing: the HTTP client, with its signature and its protection against calls to internal addresses, is
 * built in U9, which replaces this class. Until then a webhook that fires is logged as a failed attempt, so nothing
 * is lost silently and no unprotected call leaves the platform.
 */
@Component
class NoWebhookTransport implements WebhookTransport {

	static final String ERROR_KIND = "no_transport";

	@Override
	public Result send(WebhookCall call) {
		return Result.failed(ERROR_KIND);
	}
}
