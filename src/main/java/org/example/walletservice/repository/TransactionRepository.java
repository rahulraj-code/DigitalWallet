package org.example.walletservice.repository;

import org.example.walletservice.models.Transaction;
import org.example.walletservice.models.enums.TransactionType;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public class TransactionRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final RowMapper<Transaction> ROW_MAPPER = (rs, t) -> Transaction.builder()
            .transactionId(rs.getLong("transaction_id"))
            .type(TransactionType.valueOf(rs.getString("type")))
            .walletId(rs.getLong("wallet_id"))
            .accountId(rs.getString("account_id"))
            .amount(rs.getBigDecimal("amount"))
            .sagaId(rs.getLong("saga_id"))
            .idempotencyKey(rs.getString("idempotency_key"))
            .details(rs.getString("details"))
            .createdAt(rs.getTimestamp("created_at").toInstant())
            .build();

    public TransactionRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(Transaction txn) {
        txn.setCreatedAt(Instant.now());
        jdbc.update("""
            INSERT INTO transaction (transaction_id, type, wallet_id, account_id, amount, saga_id, idempotency_key, details, created_at)
            VALUES (:transactionId, :type::transaction_type, :walletId, :accountId, :amount, :sagaId, :idempotencyKey, :details::jsonb, :createdAt)
            """, toParams(txn));
    }

    public List<Transaction> findBySagaId(Long sagaId) {
        return jdbc.query("SELECT * FROM transaction WHERE saga_id = :sagaId",
                new MapSqlParameterSource("sagaId", sagaId), ROW_MAPPER);
    }

    public List<Transaction> findByWalletId(Long walletId) {
        return jdbc.query("SELECT * FROM transaction WHERE wallet_id = :walletId ORDER BY created_at DESC",
                new MapSqlParameterSource("walletId", walletId), ROW_MAPPER);
    }

    private MapSqlParameterSource toParams(Transaction t) {
        return new MapSqlParameterSource()
                .addValue("transactionId", t.getTransactionId())
                .addValue("type", t.getType().name())
                .addValue("walletId", t.getWalletId())
                .addValue("accountId", t.getAccountId())
                .addValue("amount", t.getAmount())
                .addValue("sagaId", t.getSagaId())
                .addValue("idempotencyKey", t.getIdempotencyKey())
                .addValue("details", t.getDetails())
                .addValue("createdAt", java.sql.Timestamp.from(t.getCreatedAt()));
    }
}
