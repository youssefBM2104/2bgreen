package com.twobgreen.platform.delivery.infrastructure;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.twobgreen.platform.delivery.application.NotificationBox;
import com.twobgreen.platform.delivery.application.NotificationNotFoundException;
import com.twobgreen.platform.delivery.domain.Notification;
import com.twobgreen.platform.delivery.domain.NotificationType;

/**
 * The notification box of the user who calls: citizens (U2) and regional officials (U16). DevOps users are told over
 * HTTP, by their webhooks. The owner is always the {@code sub} claim of the token.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@PreAuthorize("hasAnyRole('citizen', 'regional_official')")
class NotificationController {

	private final NotificationBox box;

	NotificationController(NotificationBox box) {
		this.box = box;
	}

	@GetMapping
	Box box(@AuthenticationPrincipal Jwt token) {
		NotificationBox.Content content = box.of(token.getSubject());
		return new Box(content.unreadCount(), content.latest().stream().map(Item::of).toList());
	}

	@PostMapping("/{id}/read")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void markRead(@AuthenticationPrincipal Jwt token, @PathVariable long id) {
		box.markRead(token.getSubject(), id);
	}

	@PostMapping("/read-all")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void markAllRead(@AuthenticationPrincipal Jwt token) {
		box.markAllRead(token.getSubject());
	}

	// The only error of this controller. No catch-all: it would turn the 403 of @PreAuthorize into a 500
	@ExceptionHandler(NotificationNotFoundException.class)
	ProblemDetail notFound(NotificationNotFoundException e) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
				"You have no notification with this id.");
		problem.setTitle("Notification not found");
		problem.setProperty("code", "NOTIFICATION_NOT_FOUND");
		return problem;
	}

	/**
	 * @param unreadCount the number on the badge
	 * @param items newest first
	 */
	record Box(long unreadCount, List<Item> items) {
	}

	/**
	 * @param readAt null while unread
	 */
	record Item(long id, NotificationType type, String message, Instant createdAt, Instant readAt) {

		static Item of(Notification notification) {
			return new Item(notification.id(), notification.type(), notification.message(),
					notification.createdAt(), notification.readAt());
		}
	}
}
