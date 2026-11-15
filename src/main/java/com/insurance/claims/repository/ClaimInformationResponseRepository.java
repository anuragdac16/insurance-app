package com.insurance.claims.repository;

import com.insurance.claims.domain.ClaimInformationResponse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ClaimInformationResponseRepository extends JpaRepository<ClaimInformationResponse, UUID> {
}
