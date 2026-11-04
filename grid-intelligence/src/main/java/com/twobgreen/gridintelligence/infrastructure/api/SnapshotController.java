package com.twobgreen.gridintelligence.infrastructure.api;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.twobgreen.gridintelligence.application.GetLatestSnapshot;
import com.twobgreen.gridintelligence.domain.GridSnapshot;
import com.twobgreen.gridintelligence.infrastructure.messaging.SnapshotMessages;

/**
 * {@code GET /api/v1/national/snapshots/latest} of contract K2 ({@code docs/api/openapi.yaml}). The body is written
 * by the K1 codec: one codec means it is always the K1 payload plus {@code quality} and {@code storedAt}.
 */
@RestController
class SnapshotController {

	private final GetLatestSnapshot getLatestSnapshot;

	private final SnapshotMessages messages = new SnapshotMessages();

	SnapshotController(GetLatestSnapshot getLatestSnapshot) {
		this.getLatestSnapshot = getLatestSnapshot;
	}

	@GetMapping(path = "/api/v1/national/snapshots/latest", produces = MediaType.APPLICATION_JSON_VALUE)
	String latestNational() {
		return messages.storedEvent(getLatestSnapshot.latest(GridSnapshot.NATIONAL_SCOPE));
	}
}
