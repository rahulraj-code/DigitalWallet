package org.example.walletservice.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.walletservice.models.enums.WalletStatus;
import org.example.walletservice.models.enums.WalletType;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Wallet {
    private Long id;
    private WalletType type;
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;
    private String accountId;
    @Builder.Default
    private String currency = "INR";
    @Builder.Default
    private WalletStatus status = WalletStatus.ACTIVE;
    private Instant createdAt;
    private Instant updatedAt;
}
