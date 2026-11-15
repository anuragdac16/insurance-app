package com.insurance.claims.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "claim_assignments")
public class ClaimAssignment {

    @Id
    private UUID id;

    @Column(name = "claim_id", nullable = false)
    private UUID claimId;

    @Column(name = "staff_id", nullable = false, length = 160)
    private String staffId;

    @Column(name = "assigned_by", nullable = false, length = 160)
    private String assignedBy;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "unassigned_at")
    private Instant unassignedAt;

    protected ClaimAssignment() {
    }

    public ClaimAssignment(UUID id, UUID claimId, String staffId, String assignedBy, Instant assignedAt) {
        this.id = id;
        this.claimId = claimId;
        this.staffId = staffId;
        this.assignedBy = assignedBy;
        this.assignedAt = assignedAt;
    }

    public void unassign(Instant when) {
        this.unassignedAt = when;
    }
}
