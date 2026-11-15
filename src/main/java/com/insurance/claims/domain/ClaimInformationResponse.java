package com.insurance.claims.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "claim_information_responses")
public class ClaimInformationResponse {

    @Id
    private UUID id;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "submitted_by", nullable = false, length = 160)
    private String submittedBy;

    @Column(name = "response_text", columnDefinition = "text")
    private String responseText;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    protected ClaimInformationResponse() {
    }

    public ClaimInformationResponse(UUID id, UUID requestId, String submittedBy,
                                    String responseText, Instant submittedAt) {
        this.id = id;
        this.requestId = requestId;
        this.submittedBy = submittedBy;
        this.responseText = responseText;
        this.submittedAt = submittedAt;
    }
}
