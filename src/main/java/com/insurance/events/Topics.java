package com.insurance.events;

/**
 * Kafka topic names. The single source of truth: producers, consumers and
 * {@code KafkaTopicConfig} all refer to these constants.
 */
public final class Topics {

    public static final String POLICY_ISSUED = "policy.issued";
    public static final String POLICY_CANCELLED = "policy.cancelled";
    public static final String CLAIM_STATUS_CHANGED = "claim.status-changed";

    private Topics() {
    }
}
