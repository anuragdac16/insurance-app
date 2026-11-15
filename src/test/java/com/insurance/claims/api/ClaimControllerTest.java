package com.insurance.claims.api;

import com.insurance.claims.domain.ClaimProductType;
import com.insurance.claims.domain.ClaimStatus;
import com.insurance.claims.service.ClaimService;
import com.insurance.common.ApiExceptionHandler;
import com.insurance.common.InvalidRequestException;
import com.insurance.common.ResourceNotFoundException;
import com.insurance.common.UnauthorizedRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ClaimControllerTest {

    @Mock
    private ClaimService claimService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new ClaimController(claimService))
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void submitReturnsCreatedAndDelegatesValidatedRequest() throws Exception {
        when(claimService.submit(any())).thenReturn(new ClaimDtos.SubmitResponse(
                "CLM-123456789ABC", "tracking-token", ClaimStatus.SUBMITTED, Instant.parse("2025-01-01T00:00:00Z")));

        mockMvc.perform(post("/api/v1/claims").contentType(MediaType.APPLICATION_JSON)
                        .content(validSubmitJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.claimNumber").value("CLM-123456789ABC"))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.trackingToken").value("tracking-token"));

        verify(claimService).submit(any(ClaimDtos.SubmitRequest.class));
    }

    @Test
    void submitRejectsInvalidRequestBody() throws Exception {
        mockMvc.perform(post("/api/v1/claims").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"claimantEmail\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void trackRequiresTrackingHeaderAndReturnsTrackingResponse() throws Exception {
        when(claimService.track("CLM-123456789ABC", "secret")).thenReturn(new ClaimDtos.TrackingResponse(
                "CLM-123456789ABC", "SG", ClaimProductType.MOTOR, ClaimStatus.SUBMITTED,
                Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2025-01-01T00:00:00Z"),
                List.of(), List.of(), null));

        mockMvc.perform(get("/api/v1/claims/CLM-123456789ABC").header("X-Claim-Tracking-Token", "secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimNumber").value("CLM-123456789ABC"))
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
        verify(claimService).track("CLM-123456789ABC", "secret");

        mockMvc.perform(get("/api/v1/claims/CLM-123456789ABC"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void serviceErrorsAreMappedToTheirHttpStatuses() throws Exception {
        when(claimService.track("missing", "secret"))
                .thenThrow(new ResourceNotFoundException("Claim not found"));
        mockMvc.perform(get("/api/v1/claims/missing").header("X-Claim-Tracking-Token", "secret"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Claim not found"));

        when(claimService.track("unauthorized", "wrong"))
                .thenThrow(new UnauthorizedRequestException("Invalid claim tracking credentials"));
        mockMvc.perform(get("/api/v1/claims/unauthorized").header("X-Claim-Tracking-Token", "wrong"))
                .andExpect(status().isUnauthorized());

        when(claimService.track("invalid", "secret")).thenThrow(new InvalidRequestException("Invalid request"));
        mockMvc.perform(get("/api/v1/claims/invalid").header("X-Claim-Tracking-Token", "secret"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitInformationRequiresTrackingHeaderAndValidResponse() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(claimService.submitInformation(eq("CLM-123456789ABC"), eq(requestId), eq("secret"), any()))
                .thenReturn(new ClaimDtos.InformationResponse(requestId, Instant.parse("2025-01-01T00:00:00Z")));

        mockMvc.perform(post("/api/v1/claims/CLM-123456789ABC/information-requests/{requestId}/responses", requestId)
                        .header("X-Claim-Tracking-Token", "secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"responseText\":\"Invoice attached\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(requestId.toString()));

        mockMvc.perform(post("/api/v1/claims/CLM-123456789ABC/information-requests/{requestId}/responses", requestId)
                        .header("X-Claim-Tracking-Token", "secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"responseText\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void staffSearchDelegatesFiltersAndRejectsPageSizeAboveLimit() throws Exception {
        when(claimService.search(eq(ClaimStatus.SUBMITTED), eq("sg"), eq("adjuster"), any()))
                .thenReturn(new PageImpl<>(List.of(staffClaim()), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/staff/claims")
                        .param("status", "SUBMITTED").param("marketCode", "sg")
                        .param("assignedTo", "adjuster").param("size", "10"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/staff/claims").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Page size cannot exceed 100"));
    }

    @Test
    void workloadReturnsSummaries() throws Exception {
        when(claimService.workload()).thenReturn(List.of(new ClaimDtos.WorkloadSummary("SG", "SGD",
                ClaimProductType.MOTOR, ClaimStatus.SUBMITTED, null, 2, new BigDecimal("10.00"),
                BigDecimal.ZERO)));

        mockMvc.perform(get("/api/v1/staff/claims/workload"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].marketCode").value("SG"))
                .andExpect(jsonPath("$[0].claimCount").value(2));
    }

    @Test
    void assignMapsRequestAndReturnsStaffClaim() throws Exception {
        when(claimService.assign(eq("CLM-123456789ABC"), any())).thenReturn(staffClaim());

        mockMvc.perform(post("/api/v1/staff/claims/CLM-123456789ABC/assignment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"staffId\":\"adjuster\",\"assignedBy\":\"manager\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedTo").value("adjuster"));

        mockMvc.perform(post("/api/v1/staff/claims/CLM-123456789ABC/assignment")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"staffId\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changeStatusMapsPatchRequest() throws Exception {
        when(claimService.changeStatus(eq("CLM-123456789ABC"), any())).thenReturn(staffClaim());

        mockMvc.perform(patch("/api/v1/staff/claims/CLM-123456789ABC/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"UNDER_REVIEW\",\"changedBy\":\"adjuster\",\"reason\":\"Review\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimNumber").value("CLM-123456789ABC"));
    }

    @Test
    void decideMapsRequestAndReturnsUpdatedClaim() throws Exception {
        when(claimService.decide(eq("CLM-123456789ABC"), any())).thenReturn(staffClaim());

        mockMvc.perform(post("/api/v1/staff/claims/CLM-123456789ABC/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"APPROVED\",\"rationale\":\"Covered\","
                                + "\"liabilityAmount\":100.00,\"currencyCode\":\"SGD\",\"decidedBy\":\"adjuster\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimNumber").value("CLM-123456789ABC"));
    }

    @Test
    void settleMapsRequestAndReturnsUpdatedClaim() throws Exception {
        when(claimService.settle(eq("CLM-123456789ABC"), any())).thenReturn(staffClaim());

        mockMvc.perform(post("/api/v1/staff/claims/CLM-123456789ABC/settlements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentReference\":\"PAY-1\",\"amount\":100.00,"
                                + "\"currencyCode\":\"SGD\",\"recordedBy\":\"finance\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimNumber").value("CLM-123456789ABC"));
    }

    @Test
    void requestInformationReturnsCreatedRequestId() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(claimService.requestInformation(eq("CLM-123456789ABC"), any())).thenReturn(requestId);

        mockMvc.perform(post("/api/v1/staff/claims/CLM-123456789ABC/information-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestText\":\"Send invoice\",\"requestedBy\":\"adjuster\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestId").value(requestId.toString()));
    }

    private static String validSubmitJson() {
        return """
                {
                  "claimantName": "Jane Doe",
                  "claimantEmail": "jane@example.com",
                  "claimantPhone": "+123",
                  "marketCode": "SG",
                  "productType": "MOTOR",
                  "policyNumber": "POLICY-1",
                  "incidentDate": "2024-12-01",
                  "incidentLocation": "Singapore",
                  "incidentDescription": "Vehicle collision",
                  "estimatedLossAmount": 100.00,
                  "currencyCode": "SGD"
                }
                """;
    }

    private static ClaimDtos.StaffClaimResponse staffClaim() {
        return new ClaimDtos.StaffClaimResponse("CLM-123456789ABC", "SG", ClaimProductType.MOTOR,
                "POLICY-1", LocalDate.of(2024, 12, 1), "Singapore", "Vehicle collision",
                new BigDecimal("100.00"), "SGD", ClaimStatus.UNDER_REVIEW, "adjuster",
                Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2025-01-01T00:00:00Z"));
    }
}
