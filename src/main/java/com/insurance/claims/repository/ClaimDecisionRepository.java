package com.insurance.claims.repository;

import com.insurance.claims.domain.ClaimDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Optional;

public interface ClaimDecisionRepository extends JpaRepository<ClaimDecision, UUID> {
    Optional<ClaimDecision> findFirstByClaimId(UUID claimId);
}
