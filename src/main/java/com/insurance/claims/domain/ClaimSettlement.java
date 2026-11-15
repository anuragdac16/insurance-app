package com.insurance.claims.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "claim_settlements")
public class ClaimSettlement {

    @Id
    private UUID id;

    @Column(name = "claim_id", nullable = false, unique = true)
    private UUID claimId;

    @Column(name = "payment_reference", nullable = false, unique = true, length = 100)
    private String paymentReference;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_by", nullable = false, length = 160)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "settled_at")
    private Instant settledAt;

    protected ClaimSettlement() {
    }

    public ClaimSettlement(UUID id, UUID claimId, String paymentReference, BigDecimal amount,
                           String currencyCode, String createdBy, Instant now) {
        this.id = id;
        this.claimId = claimId;
        this.paymentReference = paymentReference;
        this.amount = amount;
        this.currencyCode = currencyCode;
        this.createdBy = createdBy;
        this.status = "PAID";
        this.createdAt = now;
        this.settledAt = now;
    }
}
