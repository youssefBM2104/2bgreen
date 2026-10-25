package com.twobgreen.gridintelligence.application;

import java.time.Instant;

import com.twobgreen.gridintelligence.domain.GridSnapshot;

/**
 * A snapshot that is saved and readable, with the time it was written ({@code storedAt} in contract K1).
 */
public record StoredSnapshot(GridSnapshot snapshot, Instant storedAt) {
}
