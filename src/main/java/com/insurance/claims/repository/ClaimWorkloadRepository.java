package com.insurance.claims.repository;

import com.insurance.claims.api.ClaimDtos;
import com.insurance.claims.domain.ClaimProductType;
import com.insurance.claims.domain.ClaimStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class ClaimWorkloadRepository {

    private final JdbcTemplate jdbcTemplate;

    public ClaimWorkloadRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ClaimDtos.WorkloadSummary> summarize() {
        return jdbcTemplate.query("""
                SELECT c.market_code,
                       c.currency_code,
                       c.product_type,
                       c.status,
                       c.assigned_to,
                       count(*) AS claim_count,
                       coalesce(sum(c.estimated_loss_amount)
                           FILTER (WHERE c.status NOT IN ('REJECTED', 'SETTLED')), 0) AS estimated_exposure,
                       coalesce(sum(d.liability_amount)
                           FILTER (WHERE c.status = 'APPROVED'), 0) AS assessed_liability
                FROM claims c
                LEFT JOIN claim_decisions d ON d.claim_id = c.id
                GROUP BY c.market_code, c.currency_code, c.product_type, c.status, c.assigned_to
                ORDER BY c.market_code, c.currency_code, c.product_type, c.status, c.assigned_to
                """, this::map);
    }

    private ClaimDtos.WorkloadSummary map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ClaimDtos.WorkloadSummary(
                resultSet.getString("market_code"),
                resultSet.getString("currency_code").trim(),
                ClaimProductType.valueOf(resultSet.getString("product_type")),
                ClaimStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("assigned_to"),
                resultSet.getLong("claim_count"),
                resultSet.getBigDecimal("estimated_exposure"),
                resultSet.getBigDecimal("assessed_liability")
        );
    }
}
