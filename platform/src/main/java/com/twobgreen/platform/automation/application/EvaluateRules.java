package com.twobgreen.platform.automation.application;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionOperations;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import com.twobgreen.platform.automation.domain.AlertRule;
import com.twobgreen.platform.automation.domain.AlertRuleRepository;
import com.twobgreen.platform.automation.domain.Crossing;
import com.twobgreen.platform.automation.domain.CrossingOutcome;
import com.twobgreen.platform.automation.domain.CrossingPolicy;
import com.twobgreen.platform.automation.domain.GridObservation;
import com.twobgreen.platform.automation.domain.RuleState;
import com.twobgreen.platform.automation.domain.RuleStateRepository;
import com.twobgreen.platform.automation.domain.RuleSubject;
import com.twobgreen.platform.automation.domain.Webhook;
import com.twobgreen.platform.automation.domain.WebhookCondition;
import com.twobgreen.platform.automation.domain.WebhookRepository;
import com.twobgreen.platform.delivery.InAppNotifications;
import com.twobgreen.platform.delivery.WebhookCall;
import com.twobgreen.platform.delivery.WebhookCalls;

/**
 * The rule engine (P8): compares a stored snapshot with the active alert rules and webhooks of its scope, and hands
 * each crossing to module {@code delivery}.
 * <p>
 * One transaction per subject holds its new state and its delivery: both are stored or none is. A snapshot handed
 * over twice is recognised by the state and notifies nothing. A subject that fails does not stop the others; the
 * method then throws so that the snapshot comes again, and the subjects already done ignore it.
 */
public class EvaluateRules {

	private static final Logger log = LoggerFactory.getLogger(EvaluateRules.class);

	private static final DateTimeFormatter PARIS_TIME = DateTimeFormatter.ofPattern("dd/MM 'à' HH:mm")
			.withZone(ZoneId.of("Europe/Paris"));

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private final AlertRuleRepository alertRules;

	private final WebhookRepository webhooks;

	private final RuleStateRepository states;

	private final CrossingPolicy policy;

	private final InAppNotifications notifications;

	private final WebhookCalls webhookCalls;

	private final TransactionOperations transaction;

	private final Clock clock;

	public EvaluateRules(AlertRuleRepository alertRules, WebhookRepository webhooks, RuleStateRepository states,
			CrossingPolicy policy, InAppNotifications notifications, WebhookCalls webhookCalls,
			TransactionOperations transaction, Clock clock) {
		this.alertRules = alertRules;
		this.webhooks = webhooks;
		this.states = states;
		this.policy = policy;
		this.notifications = notifications;
		this.webhookCalls = webhookCalls;
		this.transaction = transaction;
		this.clock = clock;
	}

	/**
	 * @throws RuleEvaluationException if a subject could not be evaluated: hand the same snapshot over again
	 */
	public void onSnapshot(GridObservation snapshot) {
		if (snapshot.co2Intensity() == null) {
			// A missing value is not a crossing: the states stay as they are
			log.debug("Snapshot {} at {} has no CO2 intensity, rules not evaluated", snapshot.scope(),
					snapshot.observedAt());
			return;
		}
		int failures = 0;
		for (AlertRule rule : alertRules.findByScopeAndActiveTrue(snapshot.scope())) {
			boolean met = WebhookCondition.BELOW.isMet(snapshot.co2Intensity(), rule.threshold());
			failures += evaluate(RuleSubject.ALERT_RULE, rule.id(), rule.userId(), met, snapshot,
					() -> notifications.cleanGrid(rule.userId(), rule.id(), message(rule, snapshot)));
		}
		for (Webhook webhook : webhooks.findByScopeAndActiveTrue(snapshot.scope())) {
			if (webhook.conditionType() == WebhookCondition.BAND_CHANGED) {
				continue;
			}
			boolean met = webhook.conditionType().isMet(snapshot.co2Intensity(), webhook.threshold());
			failures += evaluate(RuleSubject.WEBHOOK, webhook.id(), webhook.userId(), met, snapshot,
					() -> webhookCalls.send(call(webhook, snapshot)));
		}
		if (failures > 0) {
			throw new RuleEvaluationException(failures + " subject(s) failed for snapshot " + snapshot.scope() + " at "
					+ snapshot.observedAt());
		}
	}

	/** Returns the number of failures, 0 or 1. */
	private int evaluate(RuleSubject type, long id, String userId, boolean met, GridObservation snapshot,
			Runnable deliver) {
		try {
			transaction.executeWithoutResult(status -> {
				RuleState previous = states.lockOfSubject(type.name(), id).orElse(null);
				Crossing crossing = policy.decide(previous, met, snapshot.observedAt(), clock.instant());
				if (crossing.outcome() == CrossingOutcome.ALREADY_SEEN) {
					return;
				}
				int moved = states.advance(userId, type.name(), id, crossing.status().name(),
						crossing.lastCrossingAt(), snapshot.observedAt());
				if (moved == 1 && crossing.fired()) {
					deliver.run();
				}
			});
			return 0;
		}
		catch (RuntimeException e) {
			// Type only: a message could quote a URL or a user's data
			log.error("Evaluation of {} {} failed for snapshot {} at {}: {}", type, id, snapshot.scope(),
					snapshot.observedAt(), e.getClass().getSimpleName());
			return 1;
		}
	}

	/** Shown in the notification box: the time, the intensity and the limit crossed (U2). */
	private static String message(AlertRule rule, GridObservation snapshot) {
		return "Réseau propre : " + plain(snapshot.co2Intensity()) + " gCO₂eq/kWh le "
				+ PARIS_TIME.format(snapshot.observedAt()) + ", sous votre seuil de " + rule.threshold() + ".";
	}

	private static WebhookCall call(Webhook webhook, GridObservation snapshot) {
		ObjectNode body = MAPPER.createObjectNode();
		body.put("event", webhook.conditionType() == WebhookCondition.BELOW ? "co2.below" : "co2.above");
		body.put("scope", snapshot.scope());
		body.put("observedAt", snapshot.observedAt().toString());
		body.put("co2Intensity", snapshot.co2Intensity());
		body.put("threshold", webhook.threshold());
		// Derived from the crossing, so a second evaluation of it could only produce the same delivery
		UUID deliveryId = UUID.nameUUIDFromBytes(("webhook:" + webhook.id() + ":" + snapshot.observedAt())
				.getBytes(StandardCharsets.UTF_8));
		return new WebhookCall(deliveryId, webhook.userId(), webhook.id(), webhook.url(), webhook.secret(),
				MAPPER.writeValueAsString(body));
	}

	private static String plain(BigDecimal value) {
		return value.stripTrailingZeros().toPlainString();
	}
}
