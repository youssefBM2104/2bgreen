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
import java.util.concurrent.atomic.AtomicInteger;
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
import com.twobgreen.platform.automation.domain.RuleStateRepository;
import com.twobgreen.platform.automation.domain.RuleSubject;
import com.twobgreen.platform.automation.domain.WebhookRepository;
import com.twobgreen.platform.delivery.InAppNotifications;
import com.twobgreen.platform.delivery.WebhookCalls;
import com.twobgreen.platform.delivery.application.NotificationBox;
import com.twobgreen.platform.delivery.domain.Notification;

/**
 * U2 from one end to the other: a citizen manages their rules with {@link ManageAlertRules}, national snapshots go
 * through the rule engine, and the result is read in the {@link NotificationBox}. The engine is built here with a
 * fixed clock and no debounce, so the sequences of the issue give exact counts. The rules are on the real national
 * scope: other tests may have rules there too, so every assertion is about the users of this test.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CleanGridAlertTests {

	private static final Instant T0 = Instant.parse("2026-11-10T12:00:00Z");

	private final String user = UUID.randomUUID().toString();

	private final AtomicInteger tick = new AtomicInteger();

	@Autowired
	private ManageAlertRules manage;

	@Autowired
	private NotificationBox box;

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

	@Test
	void sequenceOfTheIssueFillsTheBoxWithExactlyTwoNotifications() {
		manage.create(user, 30, true);

		feed(45, 38, 28, 25, 33, 29);

		NotificationBox.Content content = box.of(user);
		assertThat(content.unreadCount()).isEqualTo(2);
		// Newest first, each with the time (Paris), the intensity and the limit crossed
		assertThat(content.latest()).extracting(Notification::message).containsExactly(
				"Réseau propre : 29 gCO₂eq/kWh le 10/11 à 14:15, sous votre seuil de 30.",
				"Réseau propre : 28 gCO₂eq/kWh le 10/11 à 13:30, sous votre seuil de 30.");
	}

	@Test
	void replayedSnapshotCreatesNoDuplicate() {
		manage.create(user, 30, true);
		EvaluateRules engine = engine();

		for (int i = 0; i < 3; i++) {
			engine.onSnapshot(national(0, 45));
			engine.onSnapshot(national(1, 28));
		}

		assertThat(box.of(user).latest()).hasSize(1);
	}

	@Test
	void pausedRuleNotifiesNoMore() {
		AlertRule rule = manage.create(user, 30, true);
		feed(45, 28);
		assertThat(box.of(user).latest()).hasSize(1);

		manage.update(user, rule.id(), 30, false);
		feed(45, 20, 45, 20);

		assertThat(box.of(user).latest()).hasSize(1);
	}

	@Test
	void deletedRuleNotifiesNoMoreAndLeavesNoState() {
		AlertRule rule = manage.create(user, 30, true);
		feed(45, 28);
		assertThat(states.findBySubjectTypeAndSubjectId(RuleSubject.ALERT_RULE, rule.id())).isPresent();

		manage.delete(user, rule.id());
		feed(45, 20);

		assertThat(box.of(user).latest()).hasSize(1);
		assertThat(states.findBySubjectTypeAndSubjectId(RuleSubject.ALERT_RULE, rule.id())).isEmpty();
	}

	@Test
	void ruleSwitchedOnAgainWhileTheGridIsCleanNotifies() {
		AlertRule rule = manage.create(user, 30, true);
		feed(28);
		manage.update(user, rule.id(), 30, false);
		feed(27);

		// Without the reset the engine would still believe the user was told, and stay silent
		manage.update(user, rule.id(), 30, true);
		feed(26);

		assertThat(box.of(user).latest()).extracting(Notification::message)
				.hasSize(2)
				.first().asString().contains("26 gCO₂eq/kWh");
	}

	@Test
	void changedThresholdIsComparedAsANewRule() {
		AlertRule rule = manage.create(user, 30, true);
		feed(28);

		// 28 is not below 20: the old crossing no longer means anything
		manage.update(user, rule.id(), 20, true);
		feed(27, 19);

		assertThat(box.of(user).latest()).extracting(Notification::message)
				.hasSize(2)
				.first().asString().contains("19 gCO₂eq/kWh", "seuil de 20");
	}

	@Test
	void savingARuleWithoutChangingItDoesNotNotifyAgain() {
		AlertRule rule = manage.create(user, 30, true);
		feed(28);

		AlertRule same = manage.update(user, rule.id(), 30, true);
		feed(27);

		assertThat(same.updatedAt()).isEqualTo(rule.updatedAt());
		assertThat(box.of(user).latest()).hasSize(1);
	}

	@Test
	void eachUserOnlyFindsTheirOwnNotifications() {
		String other = UUID.randomUUID().toString();
		manage.create(user, 30, true);
		AlertRule ofOther = manage.create(other, 20, true);

		feed(45, 25);

		assertThat(box.of(user).latest()).hasSize(1);
		assertThat(box.of(other).latest()).isEmpty();
		assertThatThrownBy(() -> manage.update(user, ofOther.id(), 500, false))
				.isInstanceOf(AlertRuleNotFoundException.class);
		assertThatThrownBy(() -> manage.delete(user, ofOther.id())).isInstanceOf(AlertRuleNotFoundException.class);
		assertThat(manage.listOf(other)).containsExactly(ofOther);
	}

	@Test
	void manyCreationsAtOnceNeverPassTheLimit() {
		List<CompletableFuture<Boolean>> attempts = IntStream.range(0, 12)
				.mapToObj(i -> CompletableFuture.supplyAsync(() -> {
					try {
						manage.create(user, 10 + i, true);
						return true;
					}
					catch (AlertRuleLimitReachedException e) {
						return false;
					}
				}))
				.toList();

		long created = attempts.stream().map(CompletableFuture::join).filter(Boolean::booleanValue).count();

		assertThat(created).isEqualTo(ManageAlertRules.MAX_RULES_PER_USER);
		assertThat(alertRules.countByUserId(user)).isEqualTo(ManageAlertRules.MAX_RULES_PER_USER);
	}

	/** The next national readings, 15 minutes apart, after those already fed by this test. */
	private void feed(int... co2) {
		EvaluateRules engine = engine();
		for (int value : co2) {
			engine.onSnapshot(national(tick.getAndIncrement(), value));
		}
	}

	private EvaluateRules engine() {
		CrossingPolicy policy = new CrossingPolicy(Duration.ZERO, Duration.ofHours(4));
		return new EvaluateRules(alertRules, webhooks, states, policy, inAppNotifications, webhookCalls,
				new TransactionTemplate(transactionManager), Clock.fixed(T0, ZoneOffset.UTC));
	}

	private static GridObservation national(int index, int co2) {
		return new GridObservation(ManageAlertRules.NATIONAL, T0.plus(Duration.ofMinutes(15L * index)),
				BigDecimal.valueOf(co2));
	}
}
