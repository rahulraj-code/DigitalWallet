package org.example.walletservice.dto;

import java.math.BigDecimal;

public record TransferRequest(String idempotencyKey, Long sourceWalletId, Long destWalletId, BigDecimal amount, String initiatedBy) {}
