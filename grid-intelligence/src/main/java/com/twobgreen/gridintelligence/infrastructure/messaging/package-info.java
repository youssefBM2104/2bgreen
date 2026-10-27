/**
 * Kafka plumbing shared by the poller and the stream: topic names ({@link Topics}) and topic declarations, the K1
 * JSON codec ({@link SnapshotMessages}), and the producer publishing poller events ({@code grid.snapshot-received}).
 * Must not contain domain/business logic.
 */
package com.twobgreen.gridintelligence.infrastructure.messaging;
