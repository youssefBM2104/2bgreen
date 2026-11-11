package com.twobgreen.platform.delivery;

/** The HTTP channel: calls the URL of a webhook and logs every attempt (U9). */
public interface WebhookCalls {

	/** Makes the call and stores one log row for the attempt, whatever its result. Never throws on a failed call. */
	void send(WebhookCall call);
}
