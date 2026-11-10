package com.twobgreen.platform.delivery.application;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.twobgreen.platform.delivery.WebhookCall;
import com.twobgreen.platform.delivery.WebhookCalls;
import com.twobgreen.platform.delivery.domain.DeliveryOutcome;
import com.twobgreen.platform.delivery.domain.WebhookDelivery;
import com.twobgreen.platform.delivery.domain.WebhookDeliveryRepository;

/**
 * One attempt per call for now, logged whatever happens. The retries and the count of consecutive failures are
 * the policy of U9.
 */
@Service
class WebhookCallService implements WebhookCalls {

	private static final Logger log = LoggerFactory.getLogger(WebhookCallService.class);

	private static final int FIRST_ATTEMPT = 1;

	private final WebhookTransport transport;

	private final WebhookDeliveryRepository deliveries;

	WebhookCallService(WebhookTransport transport, WebhookDeliveryRepository deliveries) {
		this.transport = transport;
		this.deliveries = deliveries;
	}

	@Override
	public void send(WebhookCall call) {
		Instant attemptedAt = Instant.now();
		WebhookTransport.Result result;
		try {
			result = transport.send(call);
		}
		catch (RuntimeException e) {
			// The exception may quote the URL: only its type is logged
			log.warn("Webhook delivery {} failed with {}", call.deliveryId(), e.getClass().getSimpleName());
			result = WebhookTransport.Result.failed("error");
		}
		deliveries.save(new WebhookDelivery(null, call.deliveryId(), call.userId(), call.webhookId(), FIRST_ATTEMPT,
				attemptedAt, result.success() ? DeliveryOutcome.SUCCESS : DeliveryOutcome.FAILED,
				result.httpStatus(), result.errorKind()));
	}
}
