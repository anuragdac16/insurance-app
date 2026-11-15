package com.insurance.claims.api;

import com.insurance.claims.domain.ClaimStatus;
import com.insurance.claims.service.ClaimService;
import com.insurance.common.InvalidRequestException;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ClaimController {

    private final ClaimService claimService;

    public ClaimController(ClaimService claimService) {
        this.claimService = claimService;
    }

    @PostMapping("/claims")
    public ResponseEntity<ClaimDtos.SubmitResponse> submit(@Valid @RequestBody ClaimDtos.SubmitRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(claimService.submit(request));
    }

    @GetMapping("/claims/{claimNumber}")
    public ClaimDtos.TrackingResponse track(@PathVariable String claimNumber,
                                           @RequestHeader("X-Claim-Tracking-Token") String trackingToken) {
        return claimService.track(claimNumber, trackingToken);
    }

    @PostMapping("/claims/{claimNumber}/information-requests/{requestId}/responses")
    public ClaimDtos.InformationResponse submitInformation(
            @PathVariable String claimNumber,
            @PathVariable UUID requestId,
            @RequestHeader("X-Claim-Tracking-Token") String trackingToken,
            @Valid @RequestBody ClaimDtos.InformationResponseRequest request) {
        return claimService.submitInformation(claimNumber, requestId, trackingToken, request);
    }

    @GetMapping("/staff/claims")
    public Page<ClaimDtos.StaffClaimResponse> search(
            @RequestParam(required = false) ClaimStatus status,
            @RequestParam(required = false) String marketCode,
            @RequestParam(required = false) String assignedTo,
            @PageableDefault(size = 50) Pageable pageable) {
        if (pageable.getPageSize() > 100) {
            throw new InvalidRequestException("Page size cannot exceed 100");
        }
        return claimService.search(status, marketCode, assignedTo, pageable);
    }

    @GetMapping("/staff/claims/workload")
    public List<ClaimDtos.WorkloadSummary> workload() {
        return claimService.workload();
    }

    @PostMapping("/staff/claims/{claimNumber}/assignment")
    public ClaimDtos.StaffClaimResponse assign(@PathVariable String claimNumber,
                                               @Valid @RequestBody ClaimDtos.AssignRequest request) {
        return claimService.assign(claimNumber, request);
    }

    @PatchMapping("/staff/claims/{claimNumber}/status")
    public ClaimDtos.StaffClaimResponse changeStatus(
            @PathVariable String claimNumber,
            @Valid @RequestBody ClaimDtos.StatusChangeRequest request) {
        return claimService.changeStatus(claimNumber, request);
    }

    @PostMapping("/staff/claims/{claimNumber}/decision")
    public ClaimDtos.StaffClaimResponse decide(@PathVariable String claimNumber,
                                               @Valid @RequestBody ClaimDtos.DecisionRequest request) {
        return claimService.decide(claimNumber, request);
    }

    @PostMapping("/staff/claims/{claimNumber}/settlements")
    public ClaimDtos.StaffClaimResponse settle(@PathVariable String claimNumber,
                                               @Valid @RequestBody ClaimDtos.SettlementRequest request) {
        return claimService.settle(claimNumber, request);
    }

    @PostMapping("/staff/claims/{claimNumber}/information-requests")
    public ResponseEntity<Map<String, UUID>> requestInformation(
            @PathVariable String claimNumber,
            @Valid @RequestBody ClaimDtos.InformationRequestRequest request) {
        UUID requestId = claimService.requestInformation(claimNumber, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("requestId", requestId));
    }
}
