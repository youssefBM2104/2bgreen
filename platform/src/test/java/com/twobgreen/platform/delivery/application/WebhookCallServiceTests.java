package com.twobgreen.platform.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.twobgreen.platform.TestcontainersConfiguration;
import com.twobgreen.platform.delivery.WebhookCall;
import com.twobgreen.platform.delivery.domain.DeliveryOutcome;
import com.twobgreen.platform.delivery.domain.WebhookDelivery;
import com.twobgreen.platform.delivery.domain.WebhookDeliveryRepository;

/** Every attempt leaves one log row, whatever the transport answers. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class WebhookCallServiceTests {

	private static final long WEBHOOK_ID = 7;

	private final String user = UUID.randomUUID().toString();

	@Autowired
	private WebhookDeliveryRepository deliveries;

	@Test
	void answerInThe200RangeIsLoggedAsASuccess() {
		WebhookCall call = call();

		new WebhookCallService(sent -> WebhookTransport.Result.answered(204), deliveries).send(call);

		assertThat(log()).singleElement().satisfies(attempt -> {
			assertThat(attempt.deliveryId()).isEqualTo(call.deliveryId());
			assertThat(attempt.attempt()).isEqualTo(1);
			assertThat(attempt.outcome()).isEqualTo(DeliveryOutcome.SUCCESS);
			assertThat(attempt.httpStatus()).isEqualTo(204);
			assertThat(attempt.errorKind()).isNull();
		});
	}

	@Test
	void errorAnswerIsLoggedAsAFailureWithItsStatus() {
		new WebhookCallService(sent -> WebhookTransport.Result.answered(500), deliveries).send(call());

		assertThat(log()).singleElement().satisfies(attempt -> {
			assertThat(attempt.outcome()).isEqualTo(DeliveryOutcome.FAILED);
			assertThat(attempt.httpStatus()).isEqualTo(500);
		});
	}

	@Test
	void callWithoutAnswerIsLoggedWithItsReason() {
		new WebhookCallService(sent -> WebhookTransport.Result.failed("timeout"), deliveries).send(call());

		assertThat(log()).singleElement().satisfies(attempt -> {
			assertThat(attempt.outcome()).isEqualTo(DeliveryOutcome.FAILED);
			assertThat(attempt.httpStatus()).isNull();
			assertThat(attempt.errorKind()).isEqualTo("timeout");
		});
	}

	@Test
	void transportThatThrowsIsStillLogged() {
		new WebhookCallService(sent -> {
			throw new IllegalStateException("https://example.org/hook?token=abc refused");
		}, deliveries).send(call());

		assertThat(log()).singleElement().satisfies(attempt -> {
			assertThat(attempt.outcome()).isEqualTo(DeliveryOutcome.FAILED);
			assertThat(attempt.errorKind()).isEqualTo("error");
		});
	}

	@Test
	void callDoesNotPrintItsCredentials() {
		assertThat(call().toString()).doesNotContain("example.org", "s3cret", "co2");
	}

	private WebhookCall call() {
		return new WebhookCall(UUID.randomUUID(), user, WEBHOOK_ID, "https://example.org/hook?token=abc", "s3cret",
				"{\"event\":\"co2.below\"}");
	}

	private List<WebhookDelivery> log() {
		return deliveries.findByWebhookIdAndUserIdOrderByAttemptedAtDesc(WEBHOOK_ID, user);
	}
}
