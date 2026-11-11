package com.twobgreen.platform.delivery.application;

import com.twobgreen.platform.delivery.WebhookCall;

/** Port: what actually reaches the URL of a webhook. Signature, timeout and address checks belong to its adapter. */
public interface WebhookTransport {

	Result send(WebhookCall call);

	/**
	 * @param httpStatus status of the answer, null when there was none
	 * @param errorKind short reason when there was no answer (timeout, refused, forbidden address...), else null
	 */
	record Result(Integer httpStatus, String errorKind) {

		public static Result answered(int httpStatus) {
			return new Result(httpStatus, null);
		}

		public static Result failed(String errorKind) {
			return new Result(null, errorKind);
		}

		public boolean success() {
			return httpStatus != null && httpStatus >= 200 && httpStatus < 300;
		}
	}
}
