package org.example.walletservice.repository;

import org.example.walletservice.models.TransactionIntent;
import org.example.walletservice.models.enums.IntentStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public class TransactionIntentRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final RowMapper<TransactionIntent> ROW_MAPPER = (rs, t) -> TransactionIntent.builder()
            .intentId(rs.getString("intent_id"))
            .sourceWalletId(rs.getLong("source_wallet_id"))
            .destWalletId(rs.getLong("dest_wallet_id"))
            .amount(rs.getBigDecimal("amount"))
            .initiatedBy(rs.getString("initiated_by"))
            .status(IntentStatus.valueOf(rs.getString("status")))
            .sagaId(rs.getObject("saga_id") != null ? rs.getLong("saga_id") : null)
            .errorMessage(rs.getString("error_message"))
            .createdAt(rs.getTimestamp("created_at").toInstant())
            .updatedAt(rs.getTimestamp("updated_at").toInstant())
            .build();

    public TransactionIntentRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(TransactionIntent intent) {
        Instant now = Instant.now();
        intent.setCreatedAt(now);
        intent.setUpdatedAt(now);
        jdbc.update("""
            INSERT INTO transaction_intent (intent_id, source_wallet_id, dest_wallet_id, amount, initiated_by, status, created_at, updated_at)
            VALUES (:intentId, :sourceWalletId, :destWalletId, :amount, :initiatedBy, :status::intent_status, :createdAt, :updatedAt)
            """, new MapSqlParameterSource()
                .addValue("intentId", intent.getIntentId())
                .addValue("sourceWalletId", intent.getSourceWalletId())
                .addValue("destWalletId", intent.getDestWalletId())
                .addValue("amount", intent.getAmount())
                .addValue("initiatedBy", intent.getInitiatedBy())
                .addValue("status", intent.getStatus().name())
                .addValue("createdAt", java.sql.Timestamp.from(intent.getCreatedAt()))
                .addValue("updatedAt", java.sql.Timestamp.from(intent.getUpdatedAt())));
    }

    public Optional<TransactionIntent> findById(String intentId) {
        var list = jdbc.query("SELECT * FROM transaction_intent WHERE intent_id = :intentId",
                new MapSqlParameterSource("intentId", intentId), ROW_MAPPER);
        return list.stream().findFirst();
    }

    public void updateStatus(String intentId, IntentStatus status, Long sagaId, String errorMessage) {
        jdbc.update("""
            UPDATE transaction_intent SET status = :status::intent_status, saga_id = :sagaId,
                error_message = :errorMessage, updated_at = :updatedAt WHERE intent_id = :intentId
            """, new MapSqlParameterSource()
                .addValue("intentId", intentId)
                .addValue("status", status.name())
                .addValue("sagaId", sagaId)
                .addValue("errorMessage", errorMessage)
                .addValue("updatedAt", java.sql.Timestamp.from(Instant.now())));
    }
}
