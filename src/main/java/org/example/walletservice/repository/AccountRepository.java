package org.example.walletservice.repository;

import org.example.walletservice.models.Account;
import org.example.walletservice.models.enums.AccountStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public class AccountRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final RowMapper<Account> ROW_MAPPER = (rs, t) -> Account.builder()
            .id(rs.getString("id"))
            .userName(rs.getString("user_name"))
            .email(rs.getString("email"))
            .contactNumber(rs.getString("contact_number"))
            .status(AccountStatus.valueOf(rs.getString("status")))
            .createdAt(rs.getTimestamp("created_at").toInstant())
            .updatedAt(rs.getTimestamp("updated_at").toInstant())
            .build();

    public AccountRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(Account account) {
        var now = Instant.now();
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        jdbc.update("""
            INSERT INTO account (id, user_name, email, contact_number, status, created_at)
            VALUES (:id, :userName, :email, :contactNumber, :status::account_status, :createdAt)
            """, toParams(account));
    }

    public Optional<Account> findById(String id) {
        var list = jdbc.query("SELECT * FROM account WHERE id = :id",
                new MapSqlParameterSource("id", id), ROW_MAPPER);
        return list.stream().findFirst();
    }

    public Optional<Account> findByContactNumber(String contactNumber) {
        var list = jdbc.query("SELECT * FROM account WHERE contact_number = :contactNumber",
                new MapSqlParameterSource("contactNumber", contactNumber), ROW_MAPPER);
        return list.stream().findFirst();
    }

    private MapSqlParameterSource toParams(Account a) {
        return new MapSqlParameterSource()
                .addValue("id", a.getId())
                .addValue("userName", a.getUserName())
                .addValue("email", a.getEmail())
                .addValue("contactNumber", a.getContactNumber())
                .addValue("status", a.getStatus().name())
                .addValue("createdAt", java.sql.Timestamp.from(a.getCreatedAt()))
                .addValue("updatedAt", java.sql.Timestamp.from(a.getUpdatedAt()));
    }
}
