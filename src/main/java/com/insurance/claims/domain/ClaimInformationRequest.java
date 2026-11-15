package com.insurance.claims.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "claim_information_requests")
public class ClaimInformationRequest {

    @Id
    private UUID id;

    @Column(name = "claim_id", nullable = false)
    private UUID claimId;

    @Column(name = "requested_by", nullable = false, length = 160)
    private String requestedBy;

    @Column(name = "request_text", nullable = false, columnDefinition = "text")
    private String requestText;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    protected ClaimInformationRequest() {
    }

    public ClaimInformationRequest(UUID id, UUID claimId, String requestedBy, String requestText,
                                   Instant dueAt, Instant requestedAt) {
        this.id = id;
        this.claimId = claimId;
        this.requestedBy = requestedBy;
        this.requestText = requestText;
        this.dueAt = dueAt;
        this.requestedAt = requestedAt;
    }

    public UUID getId() { return id; }
    public UUID getClaimId() { return claimId; }
    public String getRequestText() { return requestText; }
    public Instant getDueAt() { return dueAt; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getRespondedAt() { return respondedAt; }
    public void markResponded(Instant at) { this.respondedAt = at; }
}
