package com.insurance.claims.repository;

import com.insurance.claims.domain.ClaimInformationRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClaimInformationRequestRepository extends JpaRepository<ClaimInformationRequest, UUID> {
    Optional<ClaimInformationRequest> findByIdAndClaimId(UUID id, UUID claimId);
    List<ClaimInformationRequest> findAllByClaimIdAndRespondedAtIsNull(UUID claimId);
    List<ClaimInformationRequest> findAllByClaimIdOrderByRequestedAtAsc(UUID claimId);
}
