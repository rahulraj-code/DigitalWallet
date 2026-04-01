package org.example.walletservice.dto;

import java.math.BigDecimal;

public record TransferRequest(Long sourceWalletId, Long destWalletId, BigDecimal amount, String initiatedBy, String idempotencyKey) {}
