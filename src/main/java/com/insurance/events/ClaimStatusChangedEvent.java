package com.insurance.events;

import com.insurance.claims.domain.ClaimStatus;

import java.time.Instant;
import java.util.UUID;

public record ClaimStatusChangedEvent(
        UUID eventId,
        int schemaVersion,
        String eventType,
        Instant occurredAt,
        UUID claimId,
        String claimNumber,
        String marketCode,
        ClaimStatus status
) {
}
