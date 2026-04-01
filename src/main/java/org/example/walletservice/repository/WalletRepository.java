package org.example.walletservice.repository;

import org.example.walletservice.models.Wallet;
import org.example.walletservice.models.enums.WalletStatus;
import org.example.walletservice.models.enums.WalletType;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class WalletRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final RowMapper<Wallet> ROW_MAPPER = (rs, t) -> Wallet.builder()
            .id(rs.getLong("id"))
            .type(WalletType.valueOf(rs.getString("type")))
            .balance(rs.getBigDecimal("balance"))
            .accountId(rs.getString("account_id"))
            .currency(rs.getString("currency"))
            .status(WalletStatus.valueOf(rs.getString("status")))
            .createdAt(rs.getTimestamp("created_at").toInstant())
            .updatedAt(rs.getTimestamp("updated_at").toInstant())
            .build();

    public WalletRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(Wallet wallet) {
        var now = Instant.now();
        wallet.setCreatedAt(now);
        wallet.setUpdatedAt(now);
        jdbc.update("""
            INSERT INTO wallet (id, type, balance, account_id, currency, status, created_at)
            VALUES (:id, :type::wallet_type, :balance, :accountId, :currency, :status::wallet_status, :createdAt)
            """, toParams(wallet));
    }

    public Optional<Wallet> findById(Long id) {
        var list = jdbc.query("SELECT * FROM wallet WHERE id = :id",
                new MapSqlParameterSource("id", id), ROW_MAPPER);
        return list.stream().findFirst();
    }

    public Optional<Wallet> findByIdForUpdate(Long id) {
        var list = jdbc.query("SELECT * FROM wallet WHERE id = :id FOR UPDATE",
                new MapSqlParameterSource("id", id), ROW_MAPPER);
        return list.stream().findFirst();
    }

    public List<Wallet> findByAccountId(String accountId) {
        return jdbc.query("SELECT * FROM wallet WHERE account_id = :accountId",
                new MapSqlParameterSource("accountId", accountId), ROW_MAPPER);
    }

    public void updateBalance(Long id, BigDecimal newBalance) {
        jdbc.update("""
            UPDATE wallet SET balance = :balance, updated_at = :updatedAt WHERE id = :id
            """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("balance", newBalance)
                .addValue("updatedAt", java.sql.Timestamp.from(Instant.now())));
    }

    private MapSqlParameterSource toParams(Wallet w) {
        return new MapSqlParameterSource()
                .addValue("id", w.getId())
                .addValue("type", w.getType().name())
                .addValue("balance", w.getBalance())
                .addValue("accountId", w.getAccountId())
                .addValue("currency", w.getCurrency())
                .addValue("status", w.getStatus().name())
                .addValue("createdAt", java.sql.Timestamp.from(w.getCreatedAt()))
                .addValue("updatedAt", java.sql.Timestamp.from(w.getUpdatedAt()));
    }
}
