package com.insurance.claims.api;

import com.insurance.claims.domain.ClaimProductType;
import com.insurance.claims.domain.ClaimStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ClaimDtos {

    private ClaimDtos() {
    }

    public record SubmitRequest(
            @NotBlank @Size(max = 200) String claimantName,
            @NotBlank @Email @Size(max = 320) String claimantEmail,
            @Size(max = 40) String claimantPhone,
            @NotBlank @Pattern(regexp = "[A-Za-z]{2,3}") String marketCode,
            @NotNull ClaimProductType productType,
            @NotBlank @Size(max = 80) String policyNumber,
            @NotNull @PastOrPresent LocalDate incidentDate,
            @Size(max = 300) String incidentLocation,
            @NotBlank @Size(max = 10000) String incidentDescription,
            @DecimalMin("0.00") BigDecimal estimatedLossAmount,
            @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currencyCode
    ) {
    }

    public record SubmitResponse(String claimNumber, String trackingToken, ClaimStatus status, Instant submittedAt) {
    }

    public record StatusEntry(ClaimStatus fromStatus, ClaimStatus toStatus, String reason, Instant changedAt) {
    }

    public record InformationRequestView(UUID id, String requestText, Instant dueAt, Instant requestedAt) {
    }

    public record DecisionView(ClaimStatus outcome, String rationale, BigDecimal liabilityAmount,
                               String currencyCode, Instant decidedAt) {
    }

    public record TrackingResponse(
            String claimNumber,
            String marketCode,
            ClaimProductType productType,
            ClaimStatus status,
            Instant submittedAt,
            Instant updatedAt,
            List<StatusEntry> history,
            List<InformationRequestView> informationRequests,
            DecisionView decision
    ) {
    }

    public record StaffClaimResponse(
            String claimNumber,
            String marketCode,
            ClaimProductType productType,
            String policyNumber,
            LocalDate incidentDate,
            String incidentLocation,
            String incidentDescription,
            BigDecimal estimatedLossAmount,
            String currencyCode,
            ClaimStatus status,
            String assignedTo,
            Instant submittedAt,
            Instant updatedAt
    ) {
    }

    public record AssignRequest(@NotBlank @Size(max = 160) String staffId,
                                @NotBlank @Size(max = 160) String assignedBy) {
    }

    public record StatusChangeRequest(@NotNull ClaimStatus status,
                                      @NotBlank @Size(max = 160) String changedBy,
                                      @Size(max = 2000) String reason) {
    }

    public record DecisionRequest(
            @NotNull ClaimStatus outcome,
            @NotBlank @Size(max = 5000) String rationale,
            @DecimalMin("0.00") BigDecimal liabilityAmount,
            @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currencyCode,
            @NotBlank @Size(max = 160) String decidedBy
    ) {
    }

    public record InformationRequestRequest(
            @NotBlank @Size(max = 5000) String requestText,
            Instant dueAt,
            @NotBlank @Size(max = 160) String requestedBy
    ) {
    }

    public record InformationResponseRequest(@NotBlank @Size(max = 10000) String responseText) {
    }

    public record InformationResponse(UUID requestId, Instant submittedAt) {
    }

    public record SettlementRequest(
            @NotBlank @Size(max = 100) String paymentReference,
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
            @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currencyCode,
            @NotBlank @Size(max = 160) String recordedBy
    ) {
    }

    public record WorkloadSummary(
            String marketCode,
            String currencyCode,
            ClaimProductType productType,
            ClaimStatus status,
            String assignedTo,
            long claimCount,
            BigDecimal estimatedLossExposure,
            BigDecimal assessedLiability
    ) {
    }
}
