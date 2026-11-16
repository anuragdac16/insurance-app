package com.insurance.claims.service;

import com.insurance.claims.api.ClaimDtos;
import com.insurance.claims.domain.Claim;
import com.insurance.claims.domain.ClaimDecision;
import com.insurance.claims.domain.ClaimInformationRequest;
import com.insurance.claims.domain.ClaimProductType;
import com.insurance.claims.domain.ClaimStatus;
import com.insurance.claims.domain.ClaimStatusHistory;
import com.insurance.claims.domain.Claimant;
import com.insurance.claims.repository.ClaimAssignmentRepository;
import com.insurance.claims.repository.ClaimDecisionRepository;
import com.insurance.claims.repository.ClaimInformationRequestRepository;
import com.insurance.claims.repository.ClaimInformationResponseRepository;
import com.insurance.claims.repository.ClaimRepository;
import com.insurance.claims.repository.ClaimSettlementRepository;
import com.insurance.claims.repository.ClaimStatusHistoryRepository;
import com.insurance.claims.repository.ClaimantRepository;
import com.insurance.claims.repository.ClaimWorkloadRepository;
import com.insurance.common.InvalidRequestException;
import com.insurance.common.ResourceNotFoundException;
import com.insurance.common.UnauthorizedRequestException;
import com.insurance.events.ClaimStatusChangedEvent;
import com.insurance.messaging.OutboxEventWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimServiceTest {

    private static final UUID CLAIM_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID CLAIMANT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final String CLAIM_NUMBER = "CLM-123456789ABC";
    private static final String TRACKING_TOKEN = "private-tracking-token";
    private static final Instant NOW = Instant.parse("2025-01-02T03:04:05Z");

    @Mock private ClaimRepository claimRepository;
    @Mock private ClaimantRepository claimantRepository;
    @Mock private ClaimStatusHistoryRepository historyRepository;
    @Mock private ClaimAssignmentRepository assignmentRepository;
    @Mock private ClaimDecisionRepository decisionRepository;
    @Mock private ClaimSettlementRepository settlementRepository;
    @Mock private ClaimWorkloadRepository workloadRepository;
    @Mock private ClaimInformationRequestRepository informationRequestRepository;
    @Mock private ClaimInformationResponseRepository informationResponseRepository;
    @Mock private OutboxEventWriter outboxEventWriter;

    private ClaimService service;

    @BeforeEach
    void setUp() {
        service = new ClaimService(claimRepository, claimantRepository, historyRepository, assignmentRepository,
                decisionRepository, settlementRepository, workloadRepository, informationRequestRepository,
                informationResponseRepository, outboxEventWriter);
    }

    @Test
    void submitCreatesClaimantClaimHistoryAndOutboxEventWithNormalizedValues() {
        when(claimantRepository.findFirstByEmailIgnoreCase("person@example.com")).thenReturn(Optional.empty());
        when(claimantRepository.save(any(Claimant.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ClaimDtos.SubmitResponse response = service.submit(submitRequest());

        assertTrue(response.claimNumber().matches("CLM-[A-F0-9]{12}"));
        assertEquals(ClaimStatus.SUBMITTED, response.status());
        assertNotNull(response.submittedAt());
        assertTrue(response.trackingToken().length() >= 40);

        ArgumentCaptor<Claimant> claimantCaptor = ArgumentCaptor.forClass(Claimant.class);
        verify(claimantRepository).save(claimantCaptor.capture());
        assertEquals("Jane Doe", claimantCaptor.getValue().getFullName());
        assertEquals("person@example.com", claimantCaptor.getValue().getEmail());
        assertEquals("+123", claimantCaptor.getValue().getPhone());

        ArgumentCaptor<Claim> claimCaptor = ArgumentCaptor.forClass(Claim.class);
        verify(claimRepository).save(claimCaptor.capture());
        Claim savedClaim = claimCaptor.getValue();
        assertEquals("SG", savedClaim.getMarketCode());
        assertEquals("SGD", savedClaim.getCurrencyCode());
        assertEquals("POLICY-1", savedClaim.getPolicyNumber());
        assertEquals("At the junction", savedClaim.getIncidentLocation());
        assertEquals(hash(response.trackingToken()), savedClaim.getTrackingTokenHash());
        assertFalse(savedClaim.getTrackingTokenHash().contains(response.trackingToken()));

        ArgumentCaptor<ClaimStatusHistory> historyCaptor = ArgumentCaptor.forClass(ClaimStatusHistory.class);
        verify(historyRepository).save(historyCaptor.capture());
        assertNull(historyCaptor.getValue().getFromStatus());
        assertEquals(ClaimStatus.SUBMITTED, historyCaptor.getValue().getToStatus());
        verify(outboxEventWriter).write(any(ClaimStatusChangedEvent.class));
    }

    @Test
    void submitReusesExistingClaimantAndStoresNullForBlankOptionalFields() {
        Claimant existing = new Claimant(CLAIMANT_ID, "Existing", "person@example.com", null, NOW);
        when(claimantRepository.findFirstByEmailIgnoreCase("person@example.com")).thenReturn(Optional.of(existing));
        ClaimDtos.SubmitRequest request = new ClaimDtos.SubmitRequest("Jane Doe", " Person@Example.com ", " ",
                "sg", ClaimProductType.MOTOR, " POLICY-1 ", LocalDate.of(2024, 12, 1), " ",
                "  Collision  ", null, "sgd");

        service.submit(request);

        verify(claimantRepository, never()).save(any(Claimant.class));
        ArgumentCaptor<Claim> claimCaptor = ArgumentCaptor.forClass(Claim.class);
        verify(claimRepository).save(claimCaptor.capture());
        assertEquals(CLAIMANT_ID, claimCaptor.getValue().getClaimantId());
        assertNull(claimCaptor.getValue().getIncidentLocation());
        assertEquals("Collision", claimCaptor.getValue().getIncidentDescription());
    }

    @Test
    void trackReturnsHistoryRequestsAndDecisionForAuthorizedClaimant() {
        Claim claim = claim(ClaimStatus.APPROVED);
        UUID requestId = UUID.randomUUID();
        ClaimInformationRequest infoRequest = new ClaimInformationRequest(requestId, CLAIM_ID, "staff",
                "Provide receipt", NOW.plusSeconds(60), NOW);
        ClaimDecision decision = new ClaimDecision(UUID.randomUUID(), CLAIM_ID, ClaimStatus.APPROVED,
                "Covered", new BigDecimal("125.00"), "SGD", "adjuster", NOW);
        when(claimRepository.findByClaimNumber(CLAIM_NUMBER)).thenReturn(Optional.of(claim));
        when(historyRepository.findAllByClaimIdOrderByChangedAtAsc(CLAIM_ID)).thenReturn(List.of(
                new ClaimStatusHistory(UUID.randomUUID(), CLAIM_ID, null, ClaimStatus.SUBMITTED,
                        "CLAIMANT", "Claim submitted", NOW)));
        when(informationRequestRepository.findAllByClaimIdOrderByRequestedAtAsc(CLAIM_ID))
                .thenReturn(List.of(infoRequest));
        when(decisionRepository.findFirstByClaimId(CLAIM_ID)).thenReturn(Optional.of(decision));

        ClaimDtos.TrackingResponse response = service.track(CLAIM_NUMBER, TRACKING_TOKEN);

        assertEquals(CLAIM_NUMBER, response.claimNumber());
        assertEquals(ClaimStatus.APPROVED, response.status());
        assertEquals(ClaimStatus.SUBMITTED, response.history().getFirst().toStatus());
        assertEquals("Provide receipt", response.informationRequests().getFirst().requestText());
        assertEquals(new BigDecimal("125.00"), response.decision().liabilityAmount());
    }

    @Test
    void trackReturnsNullDecisionWhenNoDecisionExists() {
        when(claimRepository.findByClaimNumber(CLAIM_NUMBER)).thenReturn(Optional.of(claim(ClaimStatus.SUBMITTED)));
        when(historyRepository.findAllByClaimIdOrderByChangedAtAsc(CLAIM_ID)).thenReturn(List.of());
        when(informationRequestRepository.findAllByClaimIdOrderByRequestedAtAsc(CLAIM_ID)).thenReturn(List.of());
        when(decisionRepository.findFirstByClaimId(CLAIM_ID)).thenReturn(Optional.empty());

        assertNull(service.track(CLAIM_NUMBER, TRACKING_TOKEN).decision());
    }

    @Test
    void trackRejectsUnknownClaimAndInvalidToken() {
        when(claimRepository.findByClaimNumber(CLAIM_NUMBER)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.track(CLAIM_NUMBER, TRACKING_TOKEN));

        when(claimRepository.findByClaimNumber(CLAIM_NUMBER)).thenReturn(Optional.of(claim(ClaimStatus.SUBMITTED)));
        assertThrows(UnauthorizedRequestException.class, () -> service.track(CLAIM_NUMBER, "incorrect"));
        assertThrows(UnauthorizedRequestException.class, () -> service.track(CLAIM_NUMBER, null));
        verifyNoInteractions(historyRepository, informationRequestRepository, decisionRepository);
    }

    @Test
    void searchNormalizesMarketCodeAndMapsResults() {
        Claim claim = claim(ClaimStatus.SUBMITTED);
        PageRequest pageable = PageRequest.of(0, 10);
        when(claimRepository.searchQueue(ClaimStatus.SUBMITTED, "SG", "adjuster", pageable))
                .thenReturn(new PageImpl<>(List.of(claim), pageable, 1));

        Page<ClaimDtos.StaffClaimResponse> result = service.search(ClaimStatus.SUBMITTED, "sg", "adjuster", pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals(CLAIM_NUMBER, result.getContent().getFirst().claimNumber());
        assertEquals("SG", result.getContent().getFirst().marketCode());
    }

    @Test
    void workloadReturnsRepositorySummary() {
        List<ClaimDtos.WorkloadSummary> summaries = List.of(new ClaimDtos.WorkloadSummary("SG", "SGD",
                ClaimProductType.MOTOR, ClaimStatus.SUBMITTED, null, 2, new BigDecimal("10.00"),
                BigDecimal.ZERO));
        when(workloadRepository.summarize()).thenReturn(summaries);
        assertEquals(summaries, service.workload());
    }

    @Test
    void assignmentClosesPreviousAssignmentAndEmitsEvent() {
        when(claimRepository.findByClaimNumber(CLAIM_NUMBER)).thenReturn(Optional.of(claim(ClaimStatus.SUBMITTED)));

        ClaimDtos.StaffClaimResponse response = service.assign(CLAIM_NUMBER,
                new ClaimDtos.AssignRequest(" adjuster ", " supervisor "));

        assertEquals("adjuster", response.assignedTo());
        verify(assignmentRepository).closeActiveAssignments(eq(CLAIM_ID), any(Instant.class));
        verify(assignmentRepository).save(any());
        verify(outboxEventWriter).write(any(ClaimStatusChangedEvent.class));
    }

    @Test
    void changeStatusMovesSubmittedOrAwaitingClaimsIntoReview() {
        Claim claim = claim(ClaimStatus.SUBMITTED);
        when(claimRepository.findByClaimNumber(CLAIM_NUMBER)).thenReturn(Optional.of(claim));

        ClaimDtos.StaffClaimResponse response = service.changeStatus(CLAIM_NUMBER,
                new ClaimDtos.StatusChangeRequest(ClaimStatus.UNDER_REVIEW, " reviewer ", " Begin "));

        assertEquals(ClaimStatus.UNDER_REVIEW, response.status());
        verify(historyRepository).save(any(ClaimStatusHistory.class));
        verify(outboxEventWriter).write(any(ClaimStatusChangedEvent.class));
    }

    @Test
    void changeStatusResumesAwaitingInformationClaim() {
        when(claimRepository.findByClaimNumber(CLAIM_NUMBER))
                .thenReturn(Optional.of(claim(ClaimStatus.AWAITING_INFORMATION)));

        assertEquals(ClaimStatus.UNDER_REVIEW, service.changeStatus(CLAIM_NUMBER,
                new ClaimDtos.StatusChangeRequest(ClaimStatus.UNDER_REVIEW, "reviewer", null)).status());
    }

    @Test
    void changeStatusRejectsInvalidTransitionAndTarget() {
        when(claimRepository.findByClaimNumber(CLAIM_NUMBER)).thenReturn(Optional.of(claim(ClaimStatus.APPROVED)));
        assertThrows(InvalidRequestException.class, () -> service.changeStatus(CLAIM_NUMBER,
                new ClaimDtos.StatusChangeRequest(ClaimStatus.UNDER_REVIEW, "reviewer", null)));
        when(claimRepository.findByClaimNumber(CLAIM_NUMBER)).thenReturn(Optional.of(claim(ClaimStatus.SUBMITTED)));
        assertThrows(InvalidRequestException.class, () -> service.changeStatus(CLAIM_NUMBER,
                new ClaimDtos.StatusChangeRequest(ClaimStatus.APPROVED, "reviewer", null)));
    }

    @Test
    void decideApprovesClaimAndNormalizesCurrency() {
        Claim claim = claim(ClaimStatus.UNDER_REVIEW);
        when(claimRepository.findByClaimNumber(CLAIM_NUMBER)).thenReturn(Optional.of(claim));

        ClaimDtos.StaffClaimResponse response = service.decide(CLAIM_NUMBER,
                new ClaimDtos.DecisionRequest(ClaimStatus.APPROVED, "  Covered  ",
                        new BigDecimal("100.00"), "sgd", " adjuster "));

        assertEquals(ClaimStatus.APPROVED, response.status());
        verify(decisionRepository).save(any(ClaimDecision.class));
        verify(outboxEventWriter).write(any(ClaimStatusChangedEvent.class));
    }

    @Test
    void decideAllowsRejectionWithoutLiability() {
        when(claimRepository.findByClaimNumber(CLAIM_NUMBER)).thenReturn(Optional.of(claim(ClaimStatus.UNDER_REVIEW)));
        assertEquals(ClaimStatus.REJECTED, service.decide(CLAIM_NUMBER,
                new ClaimDtos.DecisionRequest(ClaimStatus.REJECTED, "Not covered", null, "SGD", "adjuster")).status());
    }

    @Test
    void decideRejectsEveryInvalidOutcomePrecondition() {
        when(claimRepository.findByClaimNumber(CLAIM_NUMBER)).thenReturn(Optional.of(claim(ClaimStatus.SUBMITTED)));
        assertThrows(InvalidRequestException.class, () -> service.decide(CLAIM_NUMBER,
                decisionRequest(ClaimStatus.APPROVED, BigDecimal.ONE, "SGD")));

        when(claimRepository.findByClaimNumber(CLAIM_NUMBER)).thenReturn(Optional.of(claim(ClaimStatus.UNDER_REVIEW)));
        assertThrows(InvalidRequestException.class, () -> service.decide(CLAIM_NUMBER,
                decisionRequest(ClaimStatus.SETTLED, BigDecimal.ONE, "SGD")));
        assertThrows(InvalidRequestException.class, () -> service.decide(CLAIM_NUMBER,
                decisionRequest(ClaimStatus.APPROVED, null, "SGD")));
        assertThrows(InvalidRequestException.class, () -> service.decide(CLAIM_NUMBER,
                decisionRequest(ClaimStatus.REJECTED, BigDecimal.ONE, "SGD")));
        assertThrows(InvalidRequestException.class, () -> service.decide(CLAIM_NUMBER,
                decisionRequest(ClaimStatus.APPROVED, BigDecimal.ONE, "USD")));
        verify(decisionRepository, never()).save(any(ClaimDecision.class));
    }

    @Test
    void settleRecordsMatchingPaymentAndMovesClaimToSettled() {
        Claim claim = claim(ClaimStatus.APPROVED);
        ClaimDecision decision = new ClaimDecision(UUID.randomUUID(), CLAIM_ID, ClaimStatus.APPROVED,
                "Covered", new BigDecimal("100.00"), "SGD", "adjuster", NOW);
        when(claimRepository.findByClaimNumber(CLAIM_NUMBER)).thenReturn(Optional.of(claim));
        when(settlementRepository.existsByClaimId(CLAIM_ID)).thenReturn(false);
        when(settlementRepository.existsByPaymentReference("payment-1")).thenReturn(false);
        when(decisionRepository.findFirstByClaimId(CLAIM_ID)).thenReturn(Optional.of(decision));

        ClaimDtos.StaffClaimResponse response = service.settle(CLAIM_NUMBER,
                new ClaimDtos.SettlementRequest(" payment-1 ", new BigDecimal("100.00"), "sgd", " finance "));

        assertEquals(ClaimStatus.SETTLED, response.status());
        verify(settlementRepository).save(any());
        verify(outboxEventWriter).write(any(ClaimStatusChangedEvent.class));
    }

    @Test
    void settleRejectsInvalidStatusDuplicateMissingDecisionCurrencyOrAmount() {
        when(claimRepository.findByClaimNumber(CLAIM_NUMBER)).thenReturn(Optional.of(claim(ClaimStatus.SUBMITTED)));
        assertThrows(InvalidRequestException.class, () -> service.settle(CLAIM_NUMBER, settlementRequest()));

        when(claimRepository.findByClaimNumber(CLAIM_NUMBER)).thenReturn(Optional.of(claim(ClaimStatus.APPROVED)));
        when(settlementRepository.existsByClaimId(CLAIM_ID)).thenReturn(true);
        assertThrows(InvalidRequestException.class, () -> service.settle(CLAIM_NUMBER, settlementRequest()));

        when(settlementRepository.existsByClaimId(CLAIM_ID)).thenReturn(false);
        when(settlementRepository.existsByPaymentReference("payment-1")).thenReturn(true);
        assertThrows(InvalidRequestException.class, () -> service.settle(CLAIM_NUMBER, settlementRequest()));
        verify(settlementRepository, never()).save(any());

        when(settlementRepository.existsByPaymentReference("payment-1")).thenReturn(false);
        when(decisionRepository.findFirstByClaimId(CLAIM_ID)).thenReturn(Optional.empty());
        assertThrows(InvalidRequestException.class, () -> service.settle(CLAIM_NUMBER, settlementRequest()));

        when(decisionRepository.findFirstByClaimId(CLAIM_ID)).thenReturn(Optional.of(
                new ClaimDecision(UUID.randomUUID(), CLAIM_ID, ClaimStatus.APPROVED,
                        "Covered", BigDecimal.ONE, "USD", "adjuster", NOW)));
        assertThrows(InvalidRequestException.class, () -> service.settle(CLAIM_NUMBER, settlementRequest()));

        when(decisionRepository.findFirstByClaimId(CLAIM_ID)).thenReturn(Optional.of(
                new ClaimDecision(UUID.randomUUID(), CLAIM_ID, ClaimStatus.APPROVED,
                        "Covered", new BigDecimal("100.00"), "SGD", "adjuster", NOW)));
        assertThrows(InvalidRequestException.class, () -> service.settle(CLAIM_NUMBER, settlementRequest()));
        verify(settlementRepository, never()).save(any());
    }

    @Test
    void requestInformationPersistsRequestAndChangesStatus() {
        when(claimRepository.findByClaimNumber(CLAIM_NUMBER)).thenReturn(Optional.of(claim(ClaimStatus.UNDER_REVIEW)));

        UUID requestId = service.requestInformation(CLAIM_NUMBER,
                new ClaimDtos.InformationRequestRequest("  Send invoice  ", NOW.plusSeconds(60), " reviewer "));

        assertNotNull(requestId);
        verify(informationRequestRepository).save(any(ClaimInformationRequest.class));
        verify(historyRepository).save(any(ClaimStatusHistory.class));
        verify(outboxEventWriter).write(any(ClaimStatusChangedEvent.class));
    }

    @Test
    void requestInformationRequiresClaimUnderReview() {
        when(claimRepository.findByClaimNumber(CLAIM_NUMBER)).thenReturn(Optional.of(claim(ClaimStatus.SUBMITTED)));
        assertThrows(InvalidRequestException.class, () -> service.requestInformation(CLAIM_NUMBER,
                new ClaimDtos.InformationRequestRequest("Send invoice", null, "reviewer")));
        verify(informationRequestRepository, never()).save(any(ClaimInformationRequest.class));
    }

    @Test
    void submitInformationStoresResponseAndResumesReviewWhenAllRequestsAnswered() {
        Claim claim = claim(ClaimStatus.AWAITING_INFORMATION);
        UUID requestId = UUID.randomUUID();
        ClaimInformationRequest infoRequest = new ClaimInformationRequest(requestId, CLAIM_ID, "reviewer",
                "Send invoice", null, NOW);
        when(claimRepository.findByClaimNumber(CLAIM_NUMBER)).thenReturn(Optional.of(claim));
        when(informationRequestRepository.findByIdAndClaimId(requestId, CLAIM_ID))
                .thenReturn(Optional.of(infoRequest));
        when(informationRequestRepository.findAllByClaimIdAndRespondedAtIsNull(CLAIM_ID)).thenReturn(List.of());

        ClaimDtos.InformationResponse response = service.submitInformation(CLAIM_NUMBER, requestId,
                TRACKING_TOKEN, new ClaimDtos.InformationResponseRequest("  Invoice attached  "));

        assertEquals(requestId, response.requestId());
        assertNotNull(infoRequest.getRespondedAt());
        assertEquals(ClaimStatus.UNDER_REVIEW, claim.getStatus());
        verify(informationResponseRepository).save(any());
        verify(outboxEventWriter).write(any(ClaimStatusChangedEvent.class));
    }

    @Test
    void submitInformationDoesNotResumeWhileOtherRequestsRemainOpen() {
        Claim claim = claim(ClaimStatus.AWAITING_INFORMATION);
        UUID requestId = UUID.randomUUID();
        when(claimRepository.findByClaimNumber(CLAIM_NUMBER)).thenReturn(Optional.of(claim));
        when(informationRequestRepository.findByIdAndClaimId(requestId, CLAIM_ID)).thenReturn(Optional.of(
                new ClaimInformationRequest(requestId, CLAIM_ID, "reviewer", "Send invoice", null, NOW)));
        when(informationRequestRepository.findAllByClaimIdAndRespondedAtIsNull(CLAIM_ID))
                .thenReturn(List.of(new ClaimInformationRequest(UUID.randomUUID(), CLAIM_ID,
                        "reviewer", "Another item", null, NOW)));

        service.submitInformation(CLAIM_NUMBER, requestId, TRACKING_TOKEN,
                new ClaimDtos.InformationResponseRequest("Attached"));

        assertEquals(ClaimStatus.AWAITING_INFORMATION, claim.getStatus());
        verify(historyRepository, never()).save(any(ClaimStatusHistory.class));
    }

    @Test
    void submitInformationDoesNotChangeStatusWhenClaimIsNotAwaitingInformation() {
        Claim claim = claim(ClaimStatus.APPROVED);
        UUID requestId = UUID.randomUUID();
        when(claimRepository.findByClaimNumber(CLAIM_NUMBER)).thenReturn(Optional.of(claim));
        when(informationRequestRepository.findByIdAndClaimId(requestId, CLAIM_ID)).thenReturn(Optional.of(
                new ClaimInformationRequest(requestId, CLAIM_ID, "reviewer", "Send invoice", null, NOW)));
        when(informationRequestRepository.findAllByClaimIdAndRespondedAtIsNull(CLAIM_ID)).thenReturn(List.of());

        service.submitInformation(CLAIM_NUMBER, requestId, TRACKING_TOKEN,
                new ClaimDtos.InformationResponseRequest("Attached"));

        assertEquals(ClaimStatus.APPROVED, claim.getStatus());
        verify(historyRepository, never()).save(any(ClaimStatusHistory.class));
    }

    @Test
    void submitInformationRejectsBadTokenMissingOrAlreadyAnsweredRequest() {
        UUID requestId = UUID.randomUUID();
        Claim claim = claim(ClaimStatus.AWAITING_INFORMATION);
        when(claimRepository.findByClaimNumber(CLAIM_NUMBER)).thenReturn(Optional.of(claim));
        assertThrows(UnauthorizedRequestException.class, () -> service.submitInformation(CLAIM_NUMBER, requestId,
                "wrong", new ClaimDtos.InformationResponseRequest("Attached")));

        when(informationRequestRepository.findByIdAndClaimId(requestId, CLAIM_ID)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.submitInformation(CLAIM_NUMBER, requestId,
                TRACKING_TOKEN, new ClaimDtos.InformationResponseRequest("Attached")));

        ClaimInformationRequest answered = new ClaimInformationRequest(requestId, CLAIM_ID, "reviewer",
                "Send invoice", null, NOW);
        answered.markResponded(NOW);
        when(informationRequestRepository.findByIdAndClaimId(requestId, CLAIM_ID)).thenReturn(Optional.of(answered));
        assertThrows(InvalidRequestException.class, () -> service.submitInformation(CLAIM_NUMBER, requestId,
                TRACKING_TOKEN, new ClaimDtos.InformationResponseRequest("Attached")));
        verify(informationResponseRepository, never()).save(any());
    }

    private static Claim claim(ClaimStatus status) {
        Claim claim = new Claim(CLAIM_ID, CLAIM_NUMBER, hash(TRACKING_TOKEN), CLAIMANT_ID,
                "SG", ClaimProductType.MOTOR, "POLICY-1", LocalDate.of(2024, 12, 1),
                "Location", "Incident", new BigDecimal("100.00"), "SGD", NOW);
        if (status != ClaimStatus.SUBMITTED) {
            claim.changeStatus(status);
        }
        return claim;
    }

    private static ClaimDtos.SubmitRequest submitRequest() {
        return new ClaimDtos.SubmitRequest(" Jane Doe ", " Person@Example.com ", " +123 ", "sg",
                ClaimProductType.MOTOR, " POLICY-1 ", LocalDate.of(2024, 12, 1),
                " At the junction ", "  Collision  ", new BigDecimal("100.00"), "sgd");
    }

    private static ClaimDtos.DecisionRequest decisionRequest(ClaimStatus outcome, BigDecimal amount, String currency) {
        return new ClaimDtos.DecisionRequest(outcome, "Rationale", amount, currency, "adjuster");
    }

    private static ClaimDtos.SettlementRequest settlementRequest() {
        return new ClaimDtos.SettlementRequest("payment-1", new BigDecimal("99.00"), "SGD", "finance");
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
