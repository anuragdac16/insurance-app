package com.insurance.config;

import com.insurance.events.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the topics. Spring's KafkaAdmin creates any that do not exist when the app starts.
 * 3 partitions let up to 3 consumers in one group work in parallel; replicas = 1 because the
 * local Docker setup has a single broker.
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    NewTopic policyIssuedTopic() {
        return TopicBuilder.name(Topics.POLICY_ISSUED).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic policyCancelledTopic() {
        return TopicBuilder.name(Topics.POLICY_CANCELLED).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic claimStatusChangedTopic() {
        return TopicBuilder.name(Topics.CLAIM_STATUS_CHANGED).partitions(3).replicas(1).build();
    }
}
