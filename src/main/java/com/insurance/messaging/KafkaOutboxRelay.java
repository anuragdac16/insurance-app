package com.insurance.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Component
public class KafkaOutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(KafkaOutboxRelay.class);

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaOutboxRelay(OutboxEventRepository repository, KafkaTemplate<String, String> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:1000}")
    public void publishPendingEvents() {
        for (OutboxEventRepository.OutboxEvent event : repository.findUnpublished(100)) {
            try {
                kafkaTemplate.send(event.topic(), event.key(), event.payload()).get(10, TimeUnit.SECONDS);
                repository.markPublished(event.id(), Instant.now());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                log.warn("Outbox relay interrupted while publishing event {}", event.id(), exception);
                repository.markFailed(event.id(), exception.getMessage());
                return;
            } catch (Exception exception) {
                log.error("Outbox event {} could not be published; it will be retried", event.id(), exception);
                repository.markFailed(event.id(), exception.getMessage());
            }
        }
    }
}
