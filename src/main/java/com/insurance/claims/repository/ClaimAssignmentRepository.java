package com.insurance.claims.repository;

import com.insurance.claims.domain.ClaimAssignment;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface ClaimAssignmentRepository extends JpaRepository<ClaimAssignment, UUID> {
    @Modifying
    @Query("update ClaimAssignment a set a.unassignedAt = :when where a.claimId = :claimId and a.unassignedAt is null")
    void closeActiveAssignments(@Param("claimId") UUID claimId, @Param("when") Instant when);
}
