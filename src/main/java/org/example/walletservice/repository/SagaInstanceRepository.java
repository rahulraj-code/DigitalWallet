package org.example.walletservice.repository;

import org.example.walletservice.models.SagaInstance;
import org.example.walletservice.models.enums.SagaStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public class SagaInstanceRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final RowMapper<SagaInstance> ROW_MAPPER = (rs, t) -> SagaInstance.builder()
            .id(rs.getLong("id"))
            .sagaType(rs.getString("saga_type"))
            .status(SagaStatus.valueOf(rs.getString("status")))
            .currentStep(rs.getInt("current_step"))
            .totalSteps(rs.getInt("total_steps"))
            .payload(rs.getString("payload"))
            .ownerInstanceId(rs.getString("owner_instance_id"))
            .lastHeartbeat(rs.getTimestamp("last_heartbeat").toInstant())
            .retryCount(rs.getInt("retry_count"))
            .maxRetries(rs.getInt("max_retries"))
            .errorMessage(rs.getString("error_message"))
            .initiatedBy(rs.getString("initiated_by"))
            .expiresAt(rs.getTimestamp("expires_at").toInstant())
            .createdAt(rs.getTimestamp("created_at").toInstant())
            .updatedAt(rs.getTimestamp("updated_at").toInstant())
            .build();

    public SagaInstanceRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(SagaInstance saga) {
        var now = Instant.now();
        saga.setLastHeartbeat(now);
        saga.setCreatedAt(now);
        saga.setUpdatedAt(now);
        jdbc.update("""
            INSERT INTO saga_instance (id, saga_type, status, current_step, total_steps, payload,
                owner_instance_id, last_heartbeat, retry_count, max_retries, initiated_by, expires_at, created_at)
            VALUES (:id, :sagaType, :status::saga_status, :currentStep, :totalSteps, :payload::jsonb,
                :ownerInstanceId, :lastHeartbeat, :retryCount, :maxRetries, :initiatedBy, :expiresAt, :createdAt)
            """, toParams(saga));
    }

    public Optional<SagaInstance> findById(Long id) {
        var list = jdbc.query("SELECT * FROM saga_instance WHERE id = :id",
                new MapSqlParameterSource("id", id), ROW_MAPPER);
        return list.stream().findFirst();
    }

    public void updateStatus(Long id, SagaStatus status, int currentStep, String errorMessage) {
        jdbc.update("""
            UPDATE saga_instance SET status = :status::saga_status, current_step = :currentStep,
                error_message = :errorMessage, updated_at = :updatedAt WHERE id = :id
            """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("status", status.name())
                .addValue("currentStep", currentStep)
                .addValue("errorMessage", errorMessage)
                .addValue("updatedAt", java.sql.Timestamp.from(Instant.now())));
    }

    public void updateHeartbeat(Long id) {
        jdbc.update("UPDATE saga_instance SET last_heartbeat = :now WHERE id = :id",
                new MapSqlParameterSource("id", id)
                        .addValue("now", java.sql.Timestamp.from(Instant.now())));
    }

    private MapSqlParameterSource toParams(SagaInstance s) {
        return new MapSqlParameterSource()
                .addValue("id", s.getId())
                .addValue("sagaType", s.getSagaType())
                .addValue("status", s.getStatus().name())
                .addValue("currentStep", s.getCurrentStep())
                .addValue("totalSteps", s.getTotalSteps())
                .addValue("payload", s.getPayload())
                .addValue("ownerInstanceId", s.getOwnerInstanceId())
                .addValue("lastHeartbeat", java.sql.Timestamp.from(s.getLastHeartbeat()))
                .addValue("retryCount", s.getRetryCount())
                .addValue("maxRetries", s.getMaxRetries())
                .addValue("initiatedBy", s.getInitiatedBy())
                .addValue("expiresAt", java.sql.Timestamp.from(s.getExpiresAt()))
                .addValue("createdAt", java.sql.Timestamp.from(s.getCreatedAt()))
                .addValue("updatedAt", java.sql.Timestamp.from(s.getUpdatedAt()));
    }
}
