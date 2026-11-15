package com.insurance.claims.repository;

import com.insurance.claims.domain.Claim;
import com.insurance.claims.domain.ClaimStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ClaimRepository extends JpaRepository<Claim, UUID> {

    Optional<Claim> findByClaimNumber(String claimNumber);

    @Query("""
            select c from Claim c
            where (:status is null or c.status = :status)
              and (:marketCode is null or c.marketCode = :marketCode)
              and (:assignedTo is null or c.assignedTo = :assignedTo)
            """)
    Page<Claim> searchQueue(@Param("status") ClaimStatus status,
                            @Param("marketCode") String marketCode,
                            @Param("assignedTo") String assignedTo,
                            Pageable pageable);
}
