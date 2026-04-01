package org.example.walletservice.service;

import org.example.walletservice.models.Transaction;
import org.example.walletservice.models.Wallet;
import org.example.walletservice.models.enums.TransactionType;
import org.example.walletservice.repository.TransactionRepository;
import org.example.walletservice.repository.WalletRepository;
import org.example.walletservice.util.SnowflakeIdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class WalletOperationService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final SnowflakeIdGenerator idGenerator;

    public WalletOperationService(WalletRepository walletRepository,
                                  TransactionRepository transactionRepository,
                                  SnowflakeIdGenerator idGenerator) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public void debit(Long walletId, BigDecimal amount, long sagaId, String stepName) {
        Wallet wallet = walletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found: " + walletId));
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient balance");
        }
        walletRepository.updateBalance(walletId, wallet.getBalance().subtract(amount));
        transactionRepository.save(Transaction.builder()
                .transactionId(idGenerator.nextId())
                .type(TransactionType.DEBIT)
                .walletId(walletId)
                .accountId(wallet.getAccountId())
                .amount(amount)
                .sagaId(sagaId)
                .idempotencyKey(sagaId + ":" + stepName + ":FORWARD")
                .build());
    }

    @Transactional
    public void credit(Long walletId, BigDecimal amount, long sagaId, String stepName) {
        Wallet wallet = walletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found: " + walletId));
        walletRepository.updateBalance(walletId, wallet.getBalance().add(amount));
        transactionRepository.save(Transaction.builder()
                .transactionId(idGenerator.nextId())
                .type(TransactionType.CREDIT)
                .walletId(walletId)
                .accountId(wallet.getAccountId())
                .amount(amount)
                .sagaId(sagaId)
                .idempotencyKey(sagaId + ":" + stepName + ":FORWARD")
                .build());
    }
}
