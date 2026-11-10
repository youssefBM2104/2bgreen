package com.twobgreen.platform.automation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import com.twobgreen.platform.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AutomationUserDataTests {

	/** PostgreSQL keeps microseconds. */
	private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

	/** A fresh user per test: tests share one database and must not see each other's rows. */
	private final String user = UUID.randomUUID().toString();

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

	@Autowired
	private AlertRuleRepository rules;

	@Autowired
	private ApiKeyRepository keys;

	@Autowired
	private WebhookRepository webhooks;

	@Autowired
	private RuleStateRepository states;

	@Test
	void alertRuleIsStoredAndCounted() {
		AlertRule saved = rules.save(new AlertRule(null, user, "national", 30, true, NOW, NOW));

		assertThat(rules.findByIdAndUserId(saved.id(), user)).contains(saved);
		assertThat(rules.countByUserId(user)).isEqualTo(1);
		assertThat(rules.findByIdAndUserId(saved.id(), "someone-else")).isEmpty();
	}

	@Test
	void onlyActiveRulesAreGivenToTheRuleEngine() {
		AlertRule active = rules.save(new AlertRule(null, user, "national", 30, true, NOW, NOW));
		AlertRule paused = rules.save(new AlertRule(null, user, "national", 40, false, NOW, NOW));

		assertThat(rules.findByScopeAndActiveTrue("national")).contains(active).doesNotContain(paused);
	}

	@Test
	void thresholdOutOfRangeIsRefusedByTheDatabase() {
		assertThatThrownBy(() -> rules.save(new AlertRule(null, user, "national", 0, true, NOW, NOW)))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> rules.save(new AlertRule(null, user, "national", 1001, true, NOW, NOW)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void apiKeyIsFoundByItsHash() {
		ApiKey saved = keys.save(key(hash()));

		assertThat(keys.findByKeyHash(saved.keyHash())).contains(saved);
		assertThat(keys.findByKeyHash(hash())).isEmpty();
	}

	@Test
	void anythingButASha256IsRefusedAsAKeyHash() {
		// What a key stored in clear would look like
		assertThatThrownBy(() -> keys.save(key("2bg_this-is-the-key-itself")))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void twoKeysCannotShareAHash() {
		String hash = hash();
		keys.save(key(hash));

		assertThatThrownBy(() -> keys.save(key(hash))).isInstanceOf(DuplicateKeyException.class);
	}

	@Test
	void revokedKeysDoNotCountAsActive() {
		ApiKey first = keys.save(key(hash()));
		keys.save(key(hash()));

		keys.save(new ApiKey(first.id(), user, first.name(), first.prefix(), first.keyHash(), NOW, NOW, null));

		assertThat(keys.countByUserIdAndRevokedAtIsNull(user)).isEqualTo(1);
		assertThat(keys.findByKeyHash(first.keyHash()).orElseThrow().revokedAt()).isEqualTo(NOW);
	}

	@Test
	void callsAreCountedPerKeyAndDay() {
		ApiKey key = keys.save(key(hash()));

		assertThat(keys.callsOn(key.id(), TODAY)).isEmpty();
		assertThat(keys.countCall(key.id(), TODAY)).isEqualTo(1);
		assertThat(keys.countCall(key.id(), TODAY)).isEqualTo(2);
		assertThat(keys.countCall(key.id(), TODAY.plusDays(1))).isEqualTo(1);
		assertThat(keys.callsOn(key.id(), TODAY)).contains(2);
	}

	@Test
	void concurrentCallsLoseNoIncrement() {
		ApiKey key = keys.save(key(hash()));

		CompletableFuture.allOf(IntStream.range(0, 40)
				.mapToObj(i -> CompletableFuture.runAsync(() -> keys.countCall(key.id(), TODAY)))
				.toArray(CompletableFuture[]::new)).join();

		assertThat(keys.callsOn(key.id(), TODAY)).contains(40);
	}

	@Test
	void countersGoWithTheirKey() {
		ApiKey key = keys.save(key(hash()));
		keys.countCall(key.id(), TODAY);

		assertThat(keys.deleteAllOfUser(user)).isEqualTo(1);

		assertThat(keys.callsOn(key.id(), TODAY)).isEmpty();
	}

	@Test
	void oldCountersAndOldRevokedKeysArePurged() {
		ApiKey valid = keys.save(key(hash()));
		Instant longAgo = Instant.parse("2001-01-01T00:00:00Z");
		ApiKey revoked = keys.save(new ApiKey(null, user, "old", "2bg_old", hash(), longAgo, longAgo, null));
		keys.countCall(valid.id(), LocalDate.of(2001, 1, 1));

		assertThat(keys.deleteCountersBefore(LocalDate.of(2001, 1, 2))).isGreaterThanOrEqualTo(1);
		assertThat(keys.deleteRevokedBefore(longAgo.plusSeconds(1))).isGreaterThanOrEqualTo(1);

		assertThat(keys.findById(revoked.id())).isEmpty();
		assertThat(keys.findById(valid.id())).isPresent();
		assertThat(keys.callsOn(valid.id(), LocalDate.of(2001, 1, 1))).isEmpty();
	}

	@Test
	void webhookIsStoredWithItsCondition() {
		Webhook saved = webhooks.save(webhook(WebhookCondition.BELOW, 25));

		assertThat(webhooks.findByIdAndUserId(saved.id(), user)).contains(saved);
		assertThat(webhooks.countByUserId(user)).isEqualTo(1);
		assertThat(webhooks.findByScopeAndActiveTrue("national")).contains(saved);
	}

	@Test
	void thresholdIsRequiredExactlyWhenTheConditionIsAThreshold() {
		webhooks.save(webhook(WebhookCondition.BAND_CHANGED, null));

		assertThatThrownBy(() -> webhooks.save(webhook(WebhookCondition.BELOW, null)))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> webhooks.save(webhook(WebhookCondition.BAND_CHANGED, 25)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void webhookNeverPrintsItsUrlNorItsSecret() {
		assertThat(webhook(WebhookCondition.BELOW, 25).toString()).doesNotContain("hooks.example", "s3cret");
	}

	@Test
	void oneStatePerSubject() {
		RuleState saved = states.save(new RuleState(null, user, RuleSubject.ALERT_RULE, id(), "ABOVE", null, null));

		assertThat(states.findBySubjectTypeAndSubjectId(RuleSubject.ALERT_RULE, saved.subjectId())).contains(saved);
		assertThat(states.findBySubjectTypeAndSubjectId(RuleSubject.WEBHOOK, saved.subjectId())).isEmpty();
		assertThatThrownBy(() -> states
				.save(new RuleState(null, user, RuleSubject.ALERT_RULE, saved.subjectId(), "BELOW", NOW, NOW)))
				.isInstanceOf(DuplicateKeyException.class);
	}

	@Test
	void stateIsUpdatedInPlaceWhenTheConditionIsCrossed() {
		RuleState saved = states.save(new RuleState(null, user, RuleSubject.WEBHOOK, id(), "ABOVE", null, null));

		states.save(new RuleState(saved.id(), user, RuleSubject.WEBHOOK, saved.subjectId(), "BELOW", NOW, NOW));

		assertThat(states.findBySubjectTypeAndSubjectId(RuleSubject.WEBHOOK, saved.subjectId()).orElseThrow())
				.extracting(RuleState::state, RuleState::lastCrossingAt, RuleState::lastObservedAt)
				.containsExactly("BELOW", NOW, NOW);
	}

	@Test
	void stateOfADeletedTargetIsRemovedByItsSubject() {
		// The target lives in module regional: nothing but this call removes its state
		long target = id();
		states.save(new RuleState(null, user, RuleSubject.TARGET, target, "ON_TRACK", null, null));
		states.save(new RuleState(null, user, RuleSubject.WEBHOOK, target, "ABOVE", null, null));

		assertThat(states.deleteOfSubject(RuleSubject.TARGET.name(), target)).isEqualTo(1);

		assertThat(states.findBySubjectTypeAndSubjectId(RuleSubject.TARGET, target)).isEmpty();
		assertThat(states.findBySubjectTypeAndSubjectId(RuleSubject.WEBHOOK, target)).isPresent();
	}

	@Test
	void deletingAUserRemovesAllTheirRows() {
		rules.save(new AlertRule(null, user, "national", 30, true, NOW, NOW));
		keys.save(key(hash()));
		webhooks.save(webhook(WebhookCondition.ABOVE, 50));
		states.save(new RuleState(null, user, RuleSubject.ALERT_RULE, id(), "ABOVE", null, null));

		assertThat(rules.deleteAllOfUser(user)).isEqualTo(1);
		assertThat(keys.deleteAllOfUser(user)).isEqualTo(1);
		assertThat(webhooks.deleteAllOfUser(user)).isEqualTo(1);
		assertThat(states.deleteAllOfUser(user)).isEqualTo(1);

		assertThat(rules.findByUserIdOrderById(user)).isEmpty();
		assertThat(keys.findByUserIdOrderById(user)).isEmpty();
		assertThat(webhooks.findByUserIdOrderById(user)).isEmpty();
	}

	private ApiKey key(String hash) {
		return new ApiKey(null, user, "ci", "2bg_ab12", hash, NOW, null, null);
	}

	private Webhook webhook(WebhookCondition condition, Integer threshold) {
		return new Webhook(null, user, "https://hooks.example/abc?token=t0ken", "national", condition, threshold,
				"s3cret", true, 0, NOW);
	}

	/** 64 hex characters, different on each call. */
	private static String hash() {
		return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "");
	}

	/** An id no other test uses. */
	private static long id() {
		return UUID.randomUUID().getMostSignificantBits();
	}
}
