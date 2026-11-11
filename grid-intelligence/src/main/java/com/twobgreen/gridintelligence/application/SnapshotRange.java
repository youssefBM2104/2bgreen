package com.twobgreen.gridintelligence.application;

import java.time.Instant;
import java.util.List;

/**
 * What a range request answers: the period asked for and the snapshots found in it, ascending by {@code observedAt}.
 *
 * @param from inclusive, as asked
 * @param to exclusive, as asked
 */
public record SnapshotRange(String scope, Instant from, Instant to, List<StoredSnapshot> items) {
}
