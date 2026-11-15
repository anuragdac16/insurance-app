package com.insurance.claims.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "claims")
public class Claim {

    @Id
    private UUID id;

    @Column(name = "claim_number", nullable = false, unique = true, length = 20)
    private String claimNumber;

    @Column(name = "tracking_token_hash", nullable = false, length = 64)
    private String trackingTokenHash;

    @Column(name = "claimant_id", nullable = false)
    private UUID claimantId;

    @Column(name = "market_code", nullable = false, length = 3)
    private String marketCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 20)
    private ClaimProductType productType;

    @Column(name = "policy_number", nullable = false, length = 80)
    private String policyNumber;

    @Column(name = "incident_date", nullable = false)
    private LocalDate incidentDate;

    @Column(name = "incident_location", length = 300)
    private String incidentLocation;

    @Column(name = "incident_description", nullable = false, columnDefinition = "text")
    private String incidentDescription;

    @Column(name = "estimated_loss_amount", precision = 15, scale = 2)
    private BigDecimal estimatedLossAmount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ClaimStatus status;

    @Column(name = "assigned_to", length = 160)
    private String assignedTo;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Claim() {
    }

    public Claim(UUID id, String claimNumber, String trackingTokenHash, UUID claimantId,
                 String marketCode, ClaimProductType productType, String policyNumber,
                 LocalDate incidentDate, String incidentLocation, String incidentDescription,
                 BigDecimal estimatedLossAmount, String currencyCode, Instant now) {
        this.id = id;
        this.claimNumber = claimNumber;
        this.trackingTokenHash = trackingTokenHash;
        this.claimantId = claimantId;
        this.marketCode = marketCode;
        this.productType = productType;
        this.policyNumber = policyNumber;
        this.incidentDate = incidentDate;
        this.incidentLocation = incidentLocation;
        this.incidentDescription = incidentDescription;
        this.estimatedLossAmount = estimatedLossAmount;
        this.currencyCode = currencyCode;
        this.status = ClaimStatus.SUBMITTED;
        this.submittedAt = now;
        this.updatedAt = now;
    }

    public void assignTo(String staffId) {
        this.assignedTo = staffId;
        this.updatedAt = Instant.now();
    }

    public void changeStatus(ClaimStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getClaimNumber() { return claimNumber; }
    public String getTrackingTokenHash() { return trackingTokenHash; }
    public UUID getClaimantId() { return claimantId; }
    public String getMarketCode() { return marketCode; }
    public ClaimProductType getProductType() { return productType; }
    public String getPolicyNumber() { return policyNumber; }
    public LocalDate getIncidentDate() { return incidentDate; }
    public String getIncidentLocation() { return incidentLocation; }
    public String getIncidentDescription() { return incidentDescription; }
    public BigDecimal getEstimatedLossAmount() { return estimatedLossAmount; }
    public String getCurrencyCode() { return currencyCode; }
    public ClaimStatus getStatus() { return status; }
    public String getAssignedTo() { return assignedTo; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
