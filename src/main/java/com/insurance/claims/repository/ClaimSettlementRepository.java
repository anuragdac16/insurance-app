package com.insurance.claims.repository;

import com.insurance.claims.domain.ClaimSettlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ClaimSettlementRepository extends JpaRepository<ClaimSettlement, UUID> {
    boolean existsByClaimId(UUID claimId);
    boolean existsByPaymentReference(String paymentReference);
}
