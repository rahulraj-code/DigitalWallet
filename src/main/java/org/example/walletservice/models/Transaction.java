package org.example.walletservice.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.walletservice.models.enums.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {
    private Long transactionId;
    private TransactionType type;
    private Long walletId;
    private String accountId;
    private BigDecimal amount;
    private Long sagaId;
    private String idempotencyKey;
    private String details;
    private Instant createdAt;
}
