package com.twobgreen.platform.automation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.twobgreen.platform.TestcontainersConfiguration;
import com.twobgreen.platform.automation.domain.AlertRule;
import com.twobgreen.platform.automation.domain.AlertRuleRepository;
import com.twobgreen.platform.automation.domain.CrossingPolicy;
import com.twobgreen.platform.automation.domain.GridObservation;
import com.twobgreen.platform.automation.domain.RuleState;
import com.twobgreen.platform.automation.domain.RuleStateRepository;
import com.twobgreen.platform.automation.domain.RuleStatus;
import com.twobgreen.platform.automation.domain.RuleSubject;
import com.twobgreen.platform.automation.domain.Webhook;
import com.twobgreen.platform.automation.domain.WebhookCondition;
import com.twobgreen.platform.automation.domain.WebhookRepository;
import com.twobgreen.platform.delivery.InAppNotifications;
import com.twobgreen.platform.delivery.WebhookCalls;
import com.twobgreen.platform.delivery.domain.DeliveryOutcome;
import com.twobgreen.platform.delivery.domain.Notification;
import com.twobgreen.platform.delivery.domain.NotificationRepository;
import com.twobgreen.platform.delivery.domain.NotificationType;
import com.twobgreen.platform.delivery.domain.WebhookDelivery;
import com.twobgreen.platform.delivery.domain.WebhookDeliveryRepository;

/**
 * The engine on sequences of snapshots, against the real database. Each test builds its own engine, so the debounce
 * and the clock are chosen by the test, and works in its own scope, so tests do not evaluate each other's rules.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class EvaluateRulesTests {

	private static final Instant T0 = Instant.parse("2026-11-10T12:00:00Z");

	private static final Duration STEP = Duration.ofMinutes(15);

	private static final Duration NO_DEBOUNCE = Duration.ZERO;

	private final String user = UUID.randomUUID().toString();

	private final String scope = "test:" + UUID.randomUUID();

	@Autowired
	private AlertRuleRepository alertRules;

	@Autowired
	private WebhookRepository webhooks;

	@Autowired
	private RuleStateRepository states;

	@Autowired
	private InAppNotifications inAppNotifications;

	@Autowired
	private WebhookCalls webhookCalls;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private NotificationRepository notifications;

	@Autowired
	private WebhookDeliveryRepository deliveries;

	@Test
	void sequenceOfU2NotifiesExactlyTwice() {
		AlertRule rule = alertRule(user, 30, true);

		feed(engine(NO_DEBOUNCE), 45, 38, 28, 25, 33, 29);

		List<Notification> sent = notificationsOf(user);
		assertThat(sent).hasSize(2);
		assertThat(sent).allSatisfy(n -> {
			assertThat(n.type()).isEqualTo(NotificationType.CLEAN_GRID);
			assertThat(n.sourceId()).isEqualTo(rule.id());
			assertThat(n.readAt()).isNull();
		});
		// 12:30Z and 13:15Z are 13:30 and 14:15 in Paris
		assertThat(sent).extracting(Notification::message).containsExactlyInAnyOrder(
				"Réseau propre : 28 gCO₂eq/kWh le 10/11 à 13:30, sous votre seuil de 30.",
				"Réseau propre : 29 gCO₂eq/kWh le 10/11 à 14:15, sous votre seuil de 30.");
	}

	@Test
	void replayedSnapshotsNotifyNothingMore() {
		alertRule(user, 30, true);
		EvaluateRules engine = engine(NO_DEBOUNCE);

		// Each snapshot handed over twice, then the whole sequence again, as Kafka may do
		for (int co2 : new int[] { 45, 28, 28, 33, 33, 29, 29 }) {
			engine.onSnapshot(snapshot(indexOf(co2), co2));
		}
		feed(engine, 45, 28, 33, 29);

		assertThat(notificationsOf(user)).hasSize(2);
	}

	@Test
	void snapshotOlderThanTheLastOneEvaluatedIsIgnored() {
		AlertRule rule = alertRule(user, 30, true);
		EvaluateRules engine = engine(NO_DEBOUNCE);

		engine.onSnapshot(snapshot(3, 45));
		engine.onSnapshot(snapshot(1, 20));

		assertThat(notificationsOf(user)).isEmpty();
		assertThat(stateOf(RuleSubject.ALERT_RULE, rule.id()).lastObservedAt()).isEqualTo(at(3));
	}

	@Test
	void debounceDelaysTheSecondNotification() {
		alertRule(user, 30, true);
		EvaluateRules engine = engine(Duration.ofHours(1));

		// Fires at 12:30. Below again at 13:15, 45 minutes later: held back
		feed(engine, 45, 38, 28, 25, 33, 29);
		assertThat(notificationsOf(user)).hasSize(1);

		// 13:30, one hour after the first one and still below: the crossing was not lost
		engine.onSnapshot(snapshot(6, 27));
		assertThat(notificationsOf(user)).extracting(Notification::message)
				.anySatisfy(message -> assertThat(message).contains("27 gCO₂eq/kWh"))
				.hasSize(2);
	}

	@Test
	void valueEqualToTheLimitIsNotBelowIt() {
		alertRule(user, 30, true);

		feed(engine(NO_DEBOUNCE), 45, 30);

		assertThat(notificationsOf(user)).isEmpty();
	}

	@Test
	void ruleCreatedWhileTheGridIsAlreadyCleanNotifiesOnce() {
		alertRule(user, 30, true);

		feed(engine(NO_DEBOUNCE), 20, 21, 19);

		assertThat(notificationsOf(user)).hasSize(1);
	}

	@Test
	void pausedRuleAndRuleOfAnotherScopeStaySilent() {
		alertRule(user, 30, false);
		alertRules.save(new AlertRule(null, user, "test:" + UUID.randomUUID(), 30, true, T0, T0));

		feed(engine(NO_DEBOUNCE), 45, 20);

		assertThat(notificationsOf(user)).isEmpty();
	}

	@Test
	void eachUserIsComparedWithTheirOwnLimit() {
		String other = UUID.randomUUID().toString();
		alertRule(user, 30, true);
		alertRule(other, 20, true);

		feed(engine(NO_DEBOUNCE), 45, 25, 15);

		assertThat(notificationsOf(user)).extracting(Notification::message)
				.singleElement().asString().contains("25 gCO₂eq/kWh");
		assertThat(notificationsOf(other)).extracting(Notification::message)
				.singleElement().asString().contains("15 gCO₂eq/kWh");
	}

	@Test
	void snapshotWithoutCo2ChangesNothing() {
		AlertRule rule = alertRule(user, 30, true);
		EvaluateRules engine = engine(NO_DEBOUNCE);

		engine.onSnapshot(snapshot(0, 28));
		engine.onSnapshot(new GridObservation(scope, at(1), null));
		engine.onSnapshot(snapshot(2, 27));

		// A missing value is not a return above the limit: no second notification
		assertThat(notificationsOf(user)).hasSize(1);
		assertThat(stateOf(RuleSubject.ALERT_RULE, rule.id()).state()).isEqualTo(RuleStatus.FIRED.name());
	}

	@Test
	void backlogOfOldSnapshotsNotifiesOnlyOnceCaughtUp() {
		alertRule(user, 30, true);
		// The platform was down: it reads at 16:00 what was observed from 12:00
		EvaluateRules lateEngine = engine(NO_DEBOUNCE, T0.plus(Duration.ofHours(4)));

		feed(lateEngine, 45, 20, 45, 20);
		assertThat(notificationsOf(user)).isEmpty();

		// 15:45, read at 16:00: recent enough, and the grid is clean
		lateEngine.onSnapshot(snapshot(15, 20));
		assertThat(notificationsOf(user)).hasSize(1);
	}

	@Test
	void webhookIsCalledOncePerCrossingAndEveryAttemptIsLogged() {
		Webhook below = webhook(WebhookCondition.BELOW, 25);
		Webhook above = webhook(WebhookCondition.ABOVE, 40);
		Webhook bandChanged = webhook(WebhookCondition.BAND_CHANGED, null);

		feed(engine(NO_DEBOUNCE), 30, 20, 22, 45, 50, 24);
		feed(engine(NO_DEBOUNCE), 30, 20, 22, 45, 50, 24);

		// Below 25 at 20 and at 24, above 40 at 45
		List<WebhookDelivery> log = deliveries.findByWebhookIdAndUserIdOrderByAttemptedAtDesc(below.id(), user);
		assertThat(log).hasSize(2);
		assertThat(log).extracting(WebhookDelivery::deliveryId).doesNotHaveDuplicates();
		assertThat(log).allSatisfy(attempt -> {
			assertThat(attempt.attempt()).isEqualTo(1);
			// No HTTP client before U9: the attempt is logged as failed, not dropped
			assertThat(attempt.outcome()).isEqualTo(DeliveryOutcome.FAILED);
			assertThat(attempt.errorKind()).isEqualTo("no_transport");
			assertThat(attempt.httpStatus()).isNull();
		});
		assertThat(deliveries.findByWebhookIdAndUserIdOrderByAttemptedAtDesc(above.id(), user)).hasSize(1);
		// A colour change is not a threshold: it waits for G9
		assertThat(deliveries.findByWebhookIdAndUserIdOrderByAttemptedAtDesc(bandChanged.id(), user)).isEmpty();
		assertThat(states.findBySubjectTypeAndSubjectId(RuleSubject.WEBHOOK, bandChanged.id())).isEmpty();
		// The citizen channel is not used for a webhook
		assertThat(notificationsOf(user)).isEmpty();
	}

	@Test
	void sameSnapshotEvaluatedByEightThreadsNotifiesOnce() {
		alertRule(user, 30, true);
		EvaluateRules engine = engine(NO_DEBOUNCE);

		CompletableFuture.allOf(IntStream.range(0, 8)
				.mapToObj(i -> CompletableFuture.runAsync(() -> engine.onSnapshot(snapshot(0, 20))))
				.toArray(CompletableFuture[]::new)).join();

		assertThat(notificationsOf(user)).hasSize(1);
	}

	@Test
	void failedDeliveryLeavesTheRuleReadyForTheNextHandOver() {
		String unlucky = UUID.randomUUID().toString();
		AlertRule failing = alertRule(unlucky, 30, true);
		alertRule(user, 30, true);
		InAppNotifications broken = (userId, ruleId, message) -> {
			if (userId.equals(unlucky)) {
				throw new IllegalStateException("database down");
			}
			inAppNotifications.cleanGrid(userId, ruleId, message);
		};

		// One rule fails: the other one is still notified, and the snapshot is asked again
		assertThatThrownBy(() -> engine(NO_DEBOUNCE, T0, broken).onSnapshot(snapshot(0, 20)))
				.isInstanceOf(RuleEvaluationException.class);
		assertThat(notificationsOf(user)).hasSize(1);
		assertThat(notificationsOf(unlucky)).isEmpty();
		// State and notification go together: the failed rule kept no trace of the snapshot
		assertThat(states.findBySubjectTypeAndSubjectId(RuleSubject.ALERT_RULE, failing.id())).isEmpty();

		// Second hand-over, as Kafka does: only the rule that failed notifies
		engine(NO_DEBOUNCE).onSnapshot(snapshot(0, 20));
		assertThat(notificationsOf(user)).hasSize(1);
		assertThat(notificationsOf(unlucky)).hasSize(1);
	}

	private EvaluateRules engine(Duration minInterval) {
		return engine(minInterval, T0);
	}

	private EvaluateRules engine(Duration minInterval, Instant now) {
		return engine(minInterval, now, inAppNotifications);
	}

	private EvaluateRules engine(Duration minInterval, Instant now, InAppNotifications channel) {
		// A long maximum age: only the backlog test cares about it
		CrossingPolicy policy = new CrossingPolicy(minInterval, Duration.ofHours(2));
		return new EvaluateRules(alertRules, webhooks, states, policy, channel, webhookCalls,
				new TransactionTemplate(transactionManager), Clock.fixed(now, ZoneOffset.UTC));
	}

	/** One reading every 15 minutes from 12:00Z. The clock of the engine is 12:00Z, so none is old. */
	private void feed(EvaluateRules engine, int... co2) {
		for (int i = 0; i < co2.length; i++) {
			engine.onSnapshot(snapshot(i, co2[i]));
		}
	}

	private GridObservation snapshot(int index, int co2) {
		return new GridObservation(scope, at(index), BigDecimal.valueOf(co2));
	}

	private static Instant at(int index) {
		return T0.plus(STEP.multipliedBy(index));
	}

	/** Position in the sequence 45, 28, 33, 29 of the replay test. */
	private static int indexOf(int co2) {
		return List.of(45, 28, 33, 29).indexOf(co2);
	}

	private AlertRule alertRule(String owner, int threshold, boolean active) {
		return alertRules.save(new AlertRule(null, owner, scope, threshold, active, T0, T0));
	}

	private Webhook webhook(WebhookCondition condition, Integer threshold) {
		return webhooks.save(new Webhook(null, user, "https://example.org/hook", scope, condition, threshold,
				"secret", true, 0, T0));
	}

	private List<Notification> notificationsOf(String owner) {
		return notifications.findByUserIdOrderByCreatedAtDesc(owner);
	}

	private RuleState stateOf(RuleSubject type, long id) {
		return states.findBySubjectTypeAndSubjectId(type, id).orElseThrow();
	}
}
