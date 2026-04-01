package org.example.walletservice.dto;

public record CreateWalletRequest(String accountId, String type, String currency) {}
