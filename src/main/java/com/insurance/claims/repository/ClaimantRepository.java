package com.insurance.claims.repository;

import com.insurance.claims.domain.Claimant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Optional;

public interface ClaimantRepository extends JpaRepository<Claimant, UUID> {
    Optional<Claimant> findFirstByEmailIgnoreCase(String email);
}
