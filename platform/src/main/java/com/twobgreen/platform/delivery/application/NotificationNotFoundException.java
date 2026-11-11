package com.twobgreen.platform.delivery.application;

/** No such notification for this user. A notification of somebody else is answered the same way. */
public class NotificationNotFoundException extends RuntimeException {

	public NotificationNotFoundException(long id) {
		super("No notification " + id + " for this user");
	}
}
