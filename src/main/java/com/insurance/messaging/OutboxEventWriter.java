package com.insurance.messaging;

import com.insurance.events.ClaimStatusChangedEvent;
import com.insurance.events.Topics;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

@Component
public class OutboxEventWriter {

    private final OutboxEventRepository repository;
    private final JsonMapper jsonMapper;

    public OutboxEventWriter(OutboxEventRepository repository, JsonMapper jsonMapper) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
    }

    public void write(ClaimStatusChangedEvent event) {
        try {
            repository.append(event.eventId(), "CLAIM", event.claimId(), event.eventType(),
                    Topics.CLAIM_STATUS_CHANGED, event.claimNumber(),
                    jsonMapper.writeValueAsString(event));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize claim event", exception);
        }
    }
}
