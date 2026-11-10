package com.twobgreen.platform.automation.infrastructure;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

/**
 * The consumer of the rule engine: key and value as plain text, decoded by {@link SnapshotStoredJson}.
 * <p>
 * It has its own factory because the default one is not ours to rely on: the Kafka support of Spring Modulith, on the
 * classpath, switches it to byte arrays and adds a JSON converter, which refuses to hand a JSON object over as text.
 */
@Configuration(proxyBeanMethods = false)
class RuleEngineKafkaConfig {

	static final String CONTAINER_FACTORY = "ruleEngineListenerContainerFactory";

	/** Same connection settings as the rest of the application ({@code spring.kafka.*}), only the decoding differs. */
	@Bean(CONTAINER_FACTORY)
	ConcurrentKafkaListenerContainerFactory<String, String> ruleEngineListenerContainerFactory(
			ConsumerFactory<?, ?> applicationConsumerFactory) {
		Map<String, Object> config = new HashMap<>(applicationConsumerFactory.getConfigurationProperties());
		config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
		factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(config));
		return factory;
	}
}
