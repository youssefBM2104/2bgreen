package com.twobgreen.platform.automation.infrastructure;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.twobgreen.platform.automation.application.EvaluateRules;
import com.twobgreen.platform.automation.domain.AlertRuleRepository;
import com.twobgreen.platform.automation.domain.CrossingPolicy;
import com.twobgreen.platform.automation.domain.RuleStateRepository;
import com.twobgreen.platform.automation.domain.WebhookRepository;
import com.twobgreen.platform.delivery.InAppNotifications;
import com.twobgreen.platform.delivery.WebhookCalls;

/** Creates the rule engine from the {@code automation.rules.*} settings. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RuleEngineProperties.class)
class RuleEngineConfig {

	@Bean
	EvaluateRules evaluateRules(AlertRuleRepository alertRules, WebhookRepository webhooks,
			RuleStateRepository states, RuleEngineProperties properties, InAppNotifications notifications,
			WebhookCalls webhookCalls, PlatformTransactionManager transactionManager) {
		CrossingPolicy policy = new CrossingPolicy(properties.minInterval(), properties.maxSnapshotAge());
		return new EvaluateRules(alertRules, webhooks, states, policy, notifications, webhookCalls,
				new TransactionTemplate(transactionManager), Clock.systemUTC());
	}
}
