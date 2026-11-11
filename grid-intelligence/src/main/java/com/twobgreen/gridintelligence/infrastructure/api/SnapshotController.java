package com.twobgreen.gridintelligence.infrastructure.api;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.twobgreen.gridintelligence.application.GetLatestSnapshot;
import com.twobgreen.gridintelligence.application.GetSnapshotRange;
import com.twobgreen.gridintelligence.application.SnapshotRange;
import com.twobgreen.gridintelligence.domain.GridSnapshot;
import com.twobgreen.gridintelligence.infrastructure.messaging.SnapshotMessages;

/**
 * The snapshot endpoints of contract K2 ({@code docs/api/openapi.yaml}): the latest and a time range, for the nation
 * and for a region. Bodies are written by the K1 codec: one codec means a snapshot is always the K1 payload plus
 * {@code quality} and {@code storedAt}.
 * <p>
 * {@code from} and {@code to} arrive as text and are read by the use case, so that a missing or malformed value is
 * the documented {@code INVALID_PARAMETER} and not a framework error without a {@code code}.
 */
@RestController
class SnapshotController {

	private final GetLatestSnapshot getLatestSnapshot;

	private final GetSnapshotRange getSnapshotRange;

	private final SnapshotMessages messages = new SnapshotMessages();

	SnapshotController(GetLatestSnapshot getLatestSnapshot, GetSnapshotRange getSnapshotRange) {
		this.getLatestSnapshot = getLatestSnapshot;
		this.getSnapshotRange = getSnapshotRange;
	}

	@GetMapping(path = "/api/v1/national/snapshots/latest", produces = MediaType.APPLICATION_JSON_VALUE)
	String latestNational() {
		return messages.storedEvent(getLatestSnapshot.latest(GridSnapshot.NATIONAL_SCOPE));
	}

	@GetMapping(path = "/api/v1/national/snapshots", produces = MediaType.APPLICATION_JSON_VALUE)
	String nationalRange(@RequestParam(required = false) String from, @RequestParam(required = false) String to) {
		return write(getSnapshotRange.national(from, to));
	}

	@GetMapping(path = "/api/v1/regions/{code}/snapshots/latest", produces = MediaType.APPLICATION_JSON_VALUE)
	String latestRegion(@PathVariable String code) {
		return messages.storedEvent(getLatestSnapshot.latestOfRegion(code));
	}

	@GetMapping(path = "/api/v1/regions/{code}/snapshots", produces = MediaType.APPLICATION_JSON_VALUE)
	String regionRange(@PathVariable String code, @RequestParam(required = false) String from,
			@RequestParam(required = false) String to) {
		return write(getSnapshotRange.region(code, from, to));
	}

	private String write(SnapshotRange range) {
		return messages.range(range.scope(), range.from(), range.to(), range.items());
	}
}
