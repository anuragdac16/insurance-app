package com.insurance.claims.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "claim_decisions")
public class ClaimDecision {

    @Id
    private UUID id;

    @Column(name = "claim_id", nullable = false, unique = true)
    private UUID claimId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ClaimStatus outcome;

    @Column(nullable = false, columnDefinition = "text")
    private String rationale;

    @Column(name = "liability_amount", precision = 15, scale = 2)
    private BigDecimal liabilityAmount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "decided_by", nullable = false, length = 160)
    private String decidedBy;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    protected ClaimDecision() {
    }

    public ClaimDecision(UUID id, UUID claimId, ClaimStatus outcome, String rationale,
                        BigDecimal liabilityAmount, String currencyCode, String decidedBy,
                        Instant decidedAt) {
        this.id = id;
        this.claimId = claimId;
        this.outcome = outcome;
        this.rationale = rationale;
        this.liabilityAmount = liabilityAmount;
        this.currencyCode = currencyCode;
        this.decidedBy = decidedBy;
        this.decidedAt = decidedAt;
    }

    public BigDecimal getLiabilityAmount() { return liabilityAmount; }
    public String getCurrencyCode() { return currencyCode; }
    public ClaimStatus getOutcome() { return outcome; }
    public String getRationale() { return rationale; }
    public Instant getDecidedAt() { return decidedAt; }
}
