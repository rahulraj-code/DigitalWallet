package org.example.walletservice.repository;

import org.example.walletservice.models.SagaStepExecution;
import org.example.walletservice.models.enums.StepStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public class SagaStepExecutionRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final RowMapper<SagaStepExecution> ROW_MAPPER = (rs, t) -> SagaStepExecution.builder()
            .id(rs.getLong("id"))
            .sagaId(rs.getLong("saga_id"))
            .stepIndex(rs.getInt("step_index"))
            .stepName(rs.getString("step_name"))
            .stepType(rs.getString("step_type"))
            .status(StepStatus.valueOf(rs.getString("status")))
            .inputData(rs.getString("input_data"))
            .outputData(rs.getString("output_data"))
            .errorDetail(rs.getString("error_detail"))
            .initiatedBy(rs.getString("initiated_by"))
            .startedAt(rs.getTimestamp("started_at") != null ? rs.getTimestamp("started_at").toInstant() : null)
            .completedAt(rs.getTimestamp("completed_at") != null ? rs.getTimestamp("completed_at").toInstant() : null)
            .createdAt(rs.getTimestamp("created_at").toInstant())
            .build();

    public SagaStepExecutionRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(SagaStepExecution step) {
        step.setCreatedAt(Instant.now());
        jdbc.update("""
            INSERT INTO saga_step_execution (id, saga_id, step_index, step_name, step_type, status, input_data, initiated_by, created_at)
            VALUES (:id, :sagaId, :stepIndex, :stepName, :stepType, :status::step_status, :inputData::jsonb, :initiatedBy, :createdAt)
            """, toParams(step));
    }

    public List<SagaStepExecution> findBySagaId(Long sagaId) {
        return jdbc.query("SELECT * FROM saga_step_execution WHERE saga_id = :sagaId ORDER BY step_index",
                new MapSqlParameterSource("sagaId", sagaId), ROW_MAPPER);
    }

    public void updateStatus(Long id, StepStatus status, String outputData, String errorDetail) {
        jdbc.update("""
            UPDATE saga_step_execution SET status = :status::step_status, output_data = :outputData::jsonb,
                error_detail = :errorDetail, completed_at = :completedAt WHERE id = :id
            """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("status", status.name())
                .addValue("outputData", outputData)
                .addValue("errorDetail", errorDetail)
                .addValue("completedAt", java.sql.Timestamp.from(Instant.now())));
    }

    public void markStarted(Long id) {
        jdbc.update("""
            UPDATE saga_step_execution SET status = 'EXECUTING'::step_status, started_at = :startedAt WHERE id = :id
            """, new MapSqlParameterSource("id", id)
                .addValue("startedAt", java.sql.Timestamp.from(Instant.now())));
    }

    private MapSqlParameterSource toParams(SagaStepExecution s) {
        return new MapSqlParameterSource()
                .addValue("id", s.getId())
                .addValue("sagaId", s.getSagaId())
                .addValue("stepIndex", s.getStepIndex())
                .addValue("stepName", s.getStepName())
                .addValue("stepType", s.getStepType())
                .addValue("status", s.getStatus().name())
                .addValue("inputData", s.getInputData())
                .addValue("initiatedBy", s.getInitiatedBy())
                .addValue("createdAt", java.sql.Timestamp.from(s.getCreatedAt()));
    }
}
