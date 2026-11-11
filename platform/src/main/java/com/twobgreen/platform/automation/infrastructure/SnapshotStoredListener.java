package com.twobgreen.platform.automation.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.twobgreen.platform.automation.application.EvaluateRules;
import com.twobgreen.platform.automation.domain.GridObservation;

/**
 * Consumer of {@code grid.snapshot-stored} (contract K1), group {@code platform-rules}. The offset moves once the
 * rules are evaluated, so a crash hands the snapshot over again and the engine recognises it.
 */
@Component
class SnapshotStoredListener {

	static final String TOPIC = "grid.snapshot-stored";

	static final String GROUP = "platform-rules";

	private static final Logger log = LoggerFactory.getLogger(SnapshotStoredListener.class);

	private final EvaluateRules evaluateRules;

	SnapshotStoredListener(EvaluateRules evaluateRules) {
		this.evaluateRules = evaluateRules;
	}

	@KafkaListener(topics = TOPIC, groupId = GROUP, containerFactory = RuleEngineKafkaConfig.CONTAINER_FACTORY)
	void onSnapshotStored(String message) {
		GridObservation snapshot;
		try {
			snapshot = SnapshotStoredJson.parse(message);
		}
		catch (IllegalArgumentException e) {
			// Reading it again would fail again: log and skip, like the stream does (open point of K1)
			log.warn("Invalid message on {} skipped: {}", TOPIC, e.getMessage());
			return;
		}
		evaluateRules.onSnapshot(snapshot);
	}
}
