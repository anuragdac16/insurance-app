package com.insurance.messaging;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Repository
public class OutboxEventRepository {

    private final JdbcTemplate jdbcTemplate;

    public OutboxEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void append(UUID id, String aggregateType, UUID aggregateId, String eventType,
                       String topic, String eventKey, String payload) {
        jdbcTemplate.update("""
                INSERT INTO outbox_events
                    (id, aggregate_type, aggregate_id, event_type, topic, event_key, payload)
                VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
                """, id, aggregateType, aggregateId, eventType, topic, eventKey, payload);
    }

    public List<OutboxEvent> findUnpublished(int limit) {
        return jdbcTemplate.query("""
                SELECT id, topic, event_key, payload::text AS payload
                FROM outbox_events
                WHERE published_at IS NULL
                ORDER BY created_at, id
                LIMIT ?
                """, this::map, limit);
    }

    public void markPublished(UUID id, Instant publishedAt) {
        jdbcTemplate.update("""
                UPDATE outbox_events
                SET published_at = ?, publish_attempts = publish_attempts + 1, last_error = NULL
                WHERE id = ?
                """, new SqlParameterValue(Types.TIMESTAMP_WITH_TIMEZONE,
                publishedAt.atOffset(ZoneOffset.UTC)), id);
    }

    public void markFailed(UUID id, String error) {
        jdbcTemplate.update("""
                UPDATE outbox_events
                SET publish_attempts = publish_attempts + 1, last_error = ?
                WHERE id = ?
                """, error, id);
    }

    private OutboxEvent map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new OutboxEvent(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("topic"),
                resultSet.getString("event_key"),
                resultSet.getString("payload")
        );
    }

    public record OutboxEvent(UUID id, String topic, String key, String payload) {
    }
}
