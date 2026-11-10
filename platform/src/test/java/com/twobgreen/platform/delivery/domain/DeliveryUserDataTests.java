package com.twobgreen.platform.delivery.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import com.twobgreen.platform.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class DeliveryUserDataTests {

	/** PostgreSQL keeps microseconds. */
	private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

	/** A fresh user per test: tests share one database and must not see each other's rows. */
	private final String user = UUID.randomUUID().toString();

	@Autowired
	private NotificationRepository notifications;

	@Autowired
	private WebhookDeliveryRepository deliveries;

	@Test
	void notificationsAreListedNewestFirstAndCountedWhileUnread() {
		Notification old = notifications.save(notification(NotificationType.CLEAN_GRID, NOW.minusSeconds(60)));
		notifications.save(notification(NotificationType.TARGET_PACE, NOW));

		assertThat(notifications.findByUserIdOrderByCreatedAtDesc(user)).extracting(Notification::type)
				.containsExactly(NotificationType.TARGET_PACE, NotificationType.CLEAN_GRID);
		assertThat(notifications.countByUserIdAndReadAtIsNull(user)).isEqualTo(2);

		notifications.save(new Notification(old.id(), user, old.type(), old.sourceId(), old.message(),
				old.createdAt(), NOW));

		assertThat(notifications.countByUserIdAndReadAtIsNull(user)).isEqualTo(1);
		assertThat(notifications.findByIdAndUserId(old.id(), user).orElseThrow().readAt()).isEqualTo(NOW);
	}

	@Test
	void notificationOfAnotherUserIsNotFound() {
		Notification saved = notifications.save(notification(NotificationType.CLEAN_GRID, NOW));

		assertThat(notifications.findByIdAndUserId(saved.id(), "someone-else")).isEmpty();
	}

	@Test
	void emptyMessageIsRefusedByTheDatabase() {
		assertThatThrownBy(
				() -> notifications.save(new Notification(null, user, NotificationType.CLEAN_GRID, 1, "", NOW, null)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void attemptsOfOneDeliveryShareItsIdAndAreListedNewestFirst() {
		UUID delivery = UUID.randomUUID();
		long webhook = id();
		deliveries.save(new WebhookDelivery(null, delivery, user, webhook, 1, NOW.minusSeconds(60),
				DeliveryOutcome.FAILED, null, "TIMEOUT"));
		deliveries.save(new WebhookDelivery(null, delivery, user, webhook, 2, NOW, DeliveryOutcome.SUCCESS, 200, null));

		assertThat(deliveries.findByWebhookIdAndUserIdOrderByAttemptedAtDesc(webhook, user))
				.extracting(WebhookDelivery::attempt, WebhookDelivery::deliveryId, WebhookDelivery::httpStatus)
				.containsExactly(org.assertj.core.groups.Tuple.tuple(2, delivery, 200),
						org.assertj.core.groups.Tuple.tuple(1, delivery, null));
		assertThat(deliveries.findByWebhookIdAndUserIdOrderByAttemptedAtDesc(webhook, "someone-else")).isEmpty();
	}

	@Test
	void sameAttemptTwiceIsRefused() {
		UUID delivery = UUID.randomUUID();
		deliveries.save(new WebhookDelivery(null, delivery, user, 1, 1, NOW, DeliveryOutcome.SUCCESS, 200, null));

		assertThatThrownBy(() -> deliveries
				.save(new WebhookDelivery(null, delivery, user, 1, 1, NOW, DeliveryOutcome.SUCCESS, 200, null)))
				.isInstanceOf(DuplicateKeyException.class);
	}

	@Test
	void logOutlivesItsWebhookAndIsStillPurgedWithTheUser() {
		// No webhook with this id exists: the log only holds a plain reference
		long deletedWebhook = id();
		deliveries.save(new WebhookDelivery(null, UUID.randomUUID(), user, deletedWebhook, 1, NOW,
				DeliveryOutcome.FAILED, 500, null));
		notifications.save(notification(NotificationType.CLEAN_GRID, NOW));

		assertThat(deliveries.deleteAllOfUser(user)).isEqualTo(1);
		assertThat(notifications.deleteAllOfUser(user)).isEqualTo(1);

		assertThat(deliveries.findByWebhookIdAndUserIdOrderByAttemptedAtDesc(deletedWebhook, user)).isEmpty();
		assertThat(notifications.findByUserIdOrderByCreatedAtDesc(user)).isEmpty();
	}

	@Test
	void oldRowsArePurgedByDate() {
		Instant old = Instant.parse("2001-01-01T00:00:00Z");
		Notification notification = notifications.save(notification(NotificationType.CLEAN_GRID, old));
		WebhookDelivery delivery = deliveries.save(new WebhookDelivery(null, UUID.randomUUID(), user, 1, 1, old,
				DeliveryOutcome.SUCCESS, 204, null));

		assertThat(notifications.deleteCreatedBefore(old.plusSeconds(1))).isGreaterThanOrEqualTo(1);
		assertThat(deliveries.deleteAttemptedBefore(old.plusSeconds(1))).isGreaterThanOrEqualTo(1);

		assertThat(notifications.findById(notification.id())).isEmpty();
		assertThat(deliveries.findById(delivery.id())).isEmpty();
	}

	private Notification notification(NotificationType type, Instant createdAt) {
		return new Notification(null, user, type, 42, "CO2 at 28 g, below your limit of 30", createdAt, null);
	}

	private static long id() {
		return UUID.randomUUID().getMostSignificantBits();
	}
}
