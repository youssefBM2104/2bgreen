package com.twobgreen.platform.delivery.application;

import java.time.Instant;

import org.springframework.stereotype.Service;

import com.twobgreen.platform.delivery.InAppNotifications;
import com.twobgreen.platform.delivery.domain.Notification;
import com.twobgreen.platform.delivery.domain.NotificationRepository;
import com.twobgreen.platform.delivery.domain.NotificationType;

@Service
class InAppNotificationService implements InAppNotifications {

	private final NotificationRepository notifications;

	InAppNotificationService(NotificationRepository notifications) {
		this.notifications = notifications;
	}

	@Override
	public void cleanGrid(String userId, long alertRuleId, String message) {
		notifications.save(new Notification(null, userId, NotificationType.CLEAN_GRID, alertRuleId, message,
				Instant.now(), null));
	}
}
