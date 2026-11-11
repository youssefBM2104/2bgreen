package com.twobgreen.platform.delivery.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;

import com.twobgreen.platform.delivery.domain.Notification;
import com.twobgreen.platform.delivery.domain.NotificationRepository;

/**
 * The reading side of the in-app channel (U2, reused by U16): what a user finds in their notification box, and how
 * they mark it as read. Every method takes the user id of the caller and only reaches the notifications of that user.
 */
@Service
public class NotificationBox {

	/** A box shows the newest notifications only; older ones stay stored until the retention removes them. */
	public static final int MAX_SHOWN = 50;

	private final NotificationRepository notifications;

	NotificationBox(NotificationRepository notifications) {
		this.notifications = notifications;
	}

	public Content of(String userId) {
		return new Content(notifications.countByUserIdAndReadAtIsNull(userId),
				notifications.findLatestOfUser(userId, MAX_SHOWN));
	}

	/**
	 * Reading twice is not an error: the first date is kept.
	 *
	 * @throws NotificationNotFoundException if the user has no such notification
	 */
	public void markRead(String userId, long id) {
		if (notifications.markRead(id, userId, now()) == 0 && notifications.findByIdAndUserId(id, userId).isEmpty()) {
			throw new NotificationNotFoundException(id);
		}
	}

	public void markAllRead(String userId) {
		notifications.markAllRead(userId, now());
	}

	private static Instant now() {
		return Instant.now().truncatedTo(ChronoUnit.MICROS);
	}

	/**
	 * @param unreadCount every unread notification of the user, also those too old to be in {@code latest}
	 * @param latest newest first, at most {@link #MAX_SHOWN}
	 */
	public record Content(long unreadCount, List<Notification> latest) {
	}
}
