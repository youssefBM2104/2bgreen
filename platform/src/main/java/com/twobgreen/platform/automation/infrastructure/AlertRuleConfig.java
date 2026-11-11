package com.twobgreen.platform.automation.infrastructure;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.twobgreen.platform.automation.application.ManageAlertRules;
import com.twobgreen.platform.automation.domain.AlertRuleRepository;
import com.twobgreen.platform.automation.domain.RuleStateRepository;

/** Creates the use case behind {@link AlertRuleController}. */
@Configuration(proxyBeanMethods = false)
class AlertRuleConfig {

	@Bean
	ManageAlertRules manageAlertRules(AlertRuleRepository alertRules, RuleStateRepository states,
			PlatformTransactionManager transactionManager) {
		return new ManageAlertRules(alertRules, states, new TransactionTemplate(transactionManager),
				Clock.systemUTC());
	}
}
