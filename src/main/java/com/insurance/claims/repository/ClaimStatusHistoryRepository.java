package com.insurance.claims.repository;

import com.insurance.claims.domain.ClaimStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClaimStatusHistoryRepository extends JpaRepository<ClaimStatusHistory, UUID> {
    List<ClaimStatusHistory> findAllByClaimIdOrderByChangedAtAsc(UUID claimId);
}
