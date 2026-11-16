package com.insurance.claims.service;

import com.insurance.claims.api.ClaimDtos;
import com.insurance.claims.domain.Claim;
import com.insurance.claims.domain.ClaimAssignment;
import com.insurance.claims.domain.ClaimDecision;
import com.insurance.claims.domain.ClaimInformationRequest;
import com.insurance.claims.domain.ClaimInformationResponse;
import com.insurance.claims.domain.ClaimStatus;
import com.insurance.claims.domain.ClaimStatusHistory;
import com.insurance.claims.domain.ClaimSettlement;
import com.insurance.claims.domain.Claimant;
import com.insurance.claims.repository.ClaimAssignmentRepository;
import com.insurance.claims.repository.ClaimDecisionRepository;
import com.insurance.claims.repository.ClaimInformationRequestRepository;
import com.insurance.claims.repository.ClaimInformationResponseRepository;
import com.insurance.claims.repository.ClaimRepository;
import com.insurance.claims.repository.ClaimStatusHistoryRepository;
import com.insurance.claims.repository.ClaimSettlementRepository;
import com.insurance.claims.repository.ClaimantRepository;
import com.insurance.claims.repository.ClaimWorkloadRepository;
import com.insurance.common.InvalidRequestException;
import com.insurance.common.ResourceNotFoundException;
import com.insurance.common.UnauthorizedRequestException;
import com.insurance.events.ClaimStatusChangedEvent;
import com.insurance.messaging.OutboxEventWriter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ClaimService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ClaimRepository claimRepository;
    private final ClaimantRepository claimantRepository;
    private final ClaimStatusHistoryRepository historyRepository;
    private final ClaimAssignmentRepository assignmentRepository;
    private final ClaimDecisionRepository decisionRepository;
    private final ClaimSettlementRepository settlementRepository;
    private final ClaimWorkloadRepository workloadRepository;
    private final ClaimInformationRequestRepository informationRequestRepository;
    private final ClaimInformationResponseRepository informationResponseRepository;
    private final OutboxEventWriter outboxEventWriter;

    public ClaimService(ClaimRepository claimRepository, ClaimantRepository claimantRepository,
                        ClaimStatusHistoryRepository historyRepository,
                        ClaimAssignmentRepository assignmentRepository,
                        ClaimDecisionRepository decisionRepository,
                        ClaimSettlementRepository settlementRepository,
                        ClaimWorkloadRepository workloadRepository,
                        ClaimInformationRequestRepository informationRequestRepository,
                        ClaimInformationResponseRepository informationResponseRepository,
                        OutboxEventWriter outboxEventWriter) {
        this.claimRepository = claimRepository;
        this.claimantRepository = claimantRepository;
        this.historyRepository = historyRepository;
        this.assignmentRepository = assignmentRepository;
        this.decisionRepository = decisionRepository;
        this.settlementRepository = settlementRepository;
        this.workloadRepository = workloadRepository;
        this.informationRequestRepository = informationRequestRepository;
        this.informationResponseRepository = informationResponseRepository;
        this.outboxEventWriter = outboxEventWriter;
    }

    @Transactional
    public ClaimDtos.SubmitResponse submit(ClaimDtos.SubmitRequest request) {
        Instant now = Instant.now();
        String normalizedEmail = request.claimantEmail().trim().toLowerCase(Locale.ROOT);
        Claimant claimant = claimantRepository.findFirstByEmailIgnoreCase(normalizedEmail)
                .orElseGet(() -> claimantRepository.save(new Claimant(
                        UUID.randomUUID(), request.claimantName().trim(), normalizedEmail,
                        trimToNull(request.claimantPhone()), now)));

        UUID claimId = UUID.randomUUID();
        String claimNumber = "CLM-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toUpperCase(Locale.ROOT);
        String trackingToken = newTrackingToken();
        String marketCode = request.marketCode().toUpperCase(Locale.ROOT);
        String currencyCode = request.currencyCode().toUpperCase(Locale.ROOT);
        Claim claim = new Claim(claimId, claimNumber, hash(trackingToken), claimant.getId(),
                marketCode, request.productType(), request.policyNumber().trim(),
                request.incidentDate(), trimToNull(request.incidentLocation()),
                request.incidentDescription().trim(), request.estimatedLossAmount(), currencyCode, now);
        claimRepository.save(claim);
        historyRepository.save(new ClaimStatusHistory(UUID.randomUUID(), claimId, null,
                ClaimStatus.SUBMITTED, "CLAIMANT", "Claim submitted", now));
        recordEvent(claim, "CLAIM_SUBMITTED", now);
        return new ClaimDtos.SubmitResponse(claimNumber, trackingToken, claim.getStatus(), now);
    }

    @Transactional(readOnly = true)
    public ClaimDtos.TrackingResponse track(String claimNumber, String trackingToken) {
        Claim claim = getClaim(claimNumber);
        verifyTrackingToken(claim, trackingToken);
        List<ClaimDtos.StatusEntry> history = historyRepository
                .findAllByClaimIdOrderByChangedAtAsc(claim.getId()).stream()
                .map(item -> new ClaimDtos.StatusEntry(item.getFromStatus(), item.getToStatus(),
                        item.getReason(), item.getChangedAt()))
                .toList();
        List<ClaimDtos.InformationRequestView> requests = informationRequestRepository
                .findAllByClaimIdOrderByRequestedAtAsc(claim.getId()).stream()
                .map(item -> new ClaimDtos.InformationRequestView(
                        item.getId(), item.getRequestText(), item.getDueAt(), item.getRequestedAt()))
                .toList();
        ClaimDtos.DecisionView decision = decisionRepository.findFirstByClaimId(claim.getId())
                .map(item -> new ClaimDtos.DecisionView(item.getOutcome(), item.getRationale(),
                        item.getLiabilityAmount(), item.getCurrencyCode(), item.getDecidedAt()))
                .orElse(null);
        return new ClaimDtos.TrackingResponse(claim.getClaimNumber(), claim.getMarketCode(),
                claim.getProductType(), claim.getStatus(), claim.getSubmittedAt(), claim.getUpdatedAt(),
                history, requests, decision);
    }

    @Transactional(readOnly = true)
    public Page<ClaimDtos.StaffClaimResponse> search(ClaimStatus status, String marketCode,
                                                     String assignedTo, Pageable pageable) {
        String normalizedMarket = marketCode == null ? null : marketCode.toUpperCase(Locale.ROOT);
        return claimRepository.searchQueue(status, normalizedMarket, assignedTo, pageable)
                .map(this::toStaffResponse);
    }

    @Transactional(readOnly = true)
    public List<ClaimDtos.WorkloadSummary> workload() {
        return workloadRepository.summarize();
    }

    @Transactional
    public ClaimDtos.StaffClaimResponse assign(String claimNumber, ClaimDtos.AssignRequest request) {
        Claim claim = getClaim(claimNumber);
        Instant now = Instant.now();
        assignmentRepository.closeActiveAssignments(claim.getId(), now);
        claim.assignTo(request.staffId().trim());
        assignmentRepository.save(new ClaimAssignment(UUID.randomUUID(), claim.getId(),
                request.staffId().trim(), request.assignedBy().trim(), now));
        recordEvent(claim, "CLAIM_ASSIGNED", now);
        return toStaffResponse(claim);
    }

    @Transactional
    public ClaimDtos.StaffClaimResponse changeStatus(String claimNumber,
                                                     ClaimDtos.StatusChangeRequest request) {
        Claim claim = getClaim(claimNumber);
        ClaimStatus current = claim.getStatus();
        if (request.status() != ClaimStatus.UNDER_REVIEW
                || !(current == ClaimStatus.SUBMITTED || current == ClaimStatus.AWAITING_INFORMATION)) {
            throw new InvalidRequestException("This operation can only move SUBMITTED or AWAITING_INFORMATION claims to UNDER_REVIEW");
        }
        updateStatus(claim, request.status(), request.changedBy(), request.reason());
        return toStaffResponse(claim);
    }

    @Transactional
    public ClaimDtos.StaffClaimResponse decide(String claimNumber, ClaimDtos.DecisionRequest request) {
        Claim claim = getClaim(claimNumber);
        if (claim.getStatus() != ClaimStatus.UNDER_REVIEW) {
            throw new InvalidRequestException("A claim must be UNDER_REVIEW before a decision can be recorded");
        }
        if (request.outcome() != ClaimStatus.APPROVED && request.outcome() != ClaimStatus.REJECTED) {
            throw new InvalidRequestException("Decision outcome must be APPROVED or REJECTED");
        }
        if (request.outcome() == ClaimStatus.APPROVED && request.liabilityAmount() == null) {
            throw new InvalidRequestException("An approved claim requires an assessed liability amount");
        }
        if (request.outcome() == ClaimStatus.REJECTED
                && request.liabilityAmount() != null && request.liabilityAmount().signum() != 0) {
            throw new InvalidRequestException("A rejected claim cannot have a positive liability amount");
        }
        Instant now = Instant.now();
        String currency = request.currencyCode().toUpperCase(Locale.ROOT);
        if (!currency.equals(claim.getCurrencyCode())) {
            throw new InvalidRequestException("Decision currency must match the claim currency");
        }
        decisionRepository.save(new ClaimDecision(UUID.randomUUID(), claim.getId(), request.outcome(),
                request.rationale().trim(), request.liabilityAmount(), currency, request.decidedBy().trim(), now));
        updateStatus(claim, request.outcome(), request.decidedBy(), request.rationale());
        return toStaffResponse(claim);
    }

    @Transactional
    public ClaimDtos.StaffClaimResponse settle(String claimNumber, ClaimDtos.SettlementRequest request) {
        Claim claim = getClaim(claimNumber);
        if (claim.getStatus() != ClaimStatus.APPROVED) {
            throw new InvalidRequestException("Only an APPROVED claim can be settled");
        }
        if (settlementRepository.existsByClaimId(claim.getId())) {
            throw new InvalidRequestException("A settlement has already been recorded for this claim");
        }
        String paymentReference = request.paymentReference().trim();
        if (settlementRepository.existsByPaymentReference(paymentReference)) {
            throw new InvalidRequestException("This payment reference has already been used");
        }
        ClaimDecision decision = decisionRepository.findFirstByClaimId(claim.getId())
                .orElseThrow(() -> new InvalidRequestException("An approved decision is required before settlement"));
        String currencyCode = request.currencyCode().toUpperCase(Locale.ROOT);
        if (!currencyCode.equals(decision.getCurrencyCode())) {
            throw new InvalidRequestException("Settlement currency must match the decision currency");
        }
        if (decision.getLiabilityAmount() != null
                && request.amount().compareTo(decision.getLiabilityAmount()) != 0) {
            throw new InvalidRequestException("Settlement amount must match the recorded liability amount");
        }
        Instant now = Instant.now();
        settlementRepository.save(new ClaimSettlement(UUID.randomUUID(), claim.getId(),
                paymentReference, request.amount(), currencyCode,
                request.recordedBy().trim(), now));
        updateStatus(claim, ClaimStatus.SETTLED, request.recordedBy(), "Settlement recorded");
        return toStaffResponse(claim);
    }

    @Transactional
    public UUID requestInformation(String claimNumber, ClaimDtos.InformationRequestRequest request) {
        Claim claim = getClaim(claimNumber);
        if (claim.getStatus() != ClaimStatus.UNDER_REVIEW) {
            throw new InvalidRequestException("Information can only be requested while a claim is UNDER_REVIEW");
        }
        Instant now = Instant.now();
        UUID requestId = UUID.randomUUID();
        informationRequestRepository.save(new ClaimInformationRequest(requestId, claim.getId(),
                request.requestedBy().trim(), request.requestText().trim(), request.dueAt(), now));
        updateStatus(claim, ClaimStatus.AWAITING_INFORMATION, request.requestedBy(),
                "Additional information requested");
        return requestId;
    }

    @Transactional
    public ClaimDtos.InformationResponse submitInformation(String claimNumber, UUID requestId,
                                                           String trackingToken,
                                                           ClaimDtos.InformationResponseRequest request) {
        Claim claim = getClaim(claimNumber);
        verifyTrackingToken(claim, trackingToken);
        ClaimInformationRequest informationRequest = informationRequestRepository
                .findByIdAndClaimId(requestId, claim.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Information request not found"));
        if (informationRequest.getRespondedAt() != null) {
            throw new InvalidRequestException("This information request has already been answered");
        }
        Instant now = Instant.now();
        informationResponseRepository.save(new ClaimInformationResponse(UUID.randomUUID(), requestId,
                "CLAIMANT", request.responseText().trim(), now));
        informationRequest.markResponded(now);
        if (informationRequestRepository.findAllByClaimIdAndRespondedAtIsNull(claim.getId()).isEmpty()
                && claim.getStatus() == ClaimStatus.AWAITING_INFORMATION) {
            updateStatus(claim, ClaimStatus.UNDER_REVIEW, "CLAIMANT", "Requested information received");
        }
        return new ClaimDtos.InformationResponse(requestId, now);
    }

    private void updateStatus(Claim claim, ClaimStatus status, String changedBy, String reason) {
        Instant now = Instant.now();
        ClaimStatus previous = claim.getStatus();
        claim.changeStatus(status);
        historyRepository.save(new ClaimStatusHistory(UUID.randomUUID(), claim.getId(), previous,
                status, changedBy.trim(), trimToNull(reason), now));
        recordEvent(claim, "CLAIM_STATUS_CHANGED", now);
    }

    private void recordEvent(Claim claim, String eventType, Instant occurredAt) {
        outboxEventWriter.write(new ClaimStatusChangedEvent(UUID.randomUUID(), 1, eventType, occurredAt,
                claim.getId(), claim.getClaimNumber(), claim.getMarketCode(), claim.getStatus()));
    }

    private Claim getClaim(String claimNumber) {
        return claimRepository.findByClaimNumber(claimNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Claim not found"));
    }

    private ClaimDtos.StaffClaimResponse toStaffResponse(Claim claim) {
        return new ClaimDtos.StaffClaimResponse(claim.getClaimNumber(), claim.getMarketCode(),
                claim.getProductType(), claim.getPolicyNumber(), claim.getIncidentDate(),
                claim.getIncidentLocation(), claim.getIncidentDescription(), claim.getEstimatedLossAmount(),
                claim.getCurrencyCode(), claim.getStatus(), claim.getAssignedTo(),
                claim.getSubmittedAt(), claim.getUpdatedAt());
    }

    private void verifyTrackingToken(Claim claim, String token) {
        if (token == null || !MessageDigest.isEqual(
                claim.getTrackingTokenHash().getBytes(StandardCharsets.US_ASCII),
                hash(token).getBytes(StandardCharsets.US_ASCII))) {
            throw new UnauthorizedRequestException("Invalid claim tracking credentials");
        }
    }

    private static String newTrackingToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
