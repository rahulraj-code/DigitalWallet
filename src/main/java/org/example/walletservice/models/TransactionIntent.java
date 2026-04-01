package org.example.walletservice.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.walletservice.models.enums.IntentStatus;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionIntent {
    private String intentId;
    private Long sourceWalletId;
    private Long destWalletId;
    private BigDecimal amount;
    private String initiatedBy;
    @Builder.Default
    private IntentStatus status = IntentStatus.PENDING;
    private Long sagaId;
    private String errorMessage;
    private Instant createdAt;
    private Instant updatedAt;
}
