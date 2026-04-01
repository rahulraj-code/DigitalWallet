package org.example.walletservice.service;

import lombok.extern.slf4j.Slf4j;
import org.example.walletservice.models.Transaction;
import org.example.walletservice.models.Wallet;
import org.example.walletservice.models.enums.TransactionType;
import org.example.walletservice.repository.TransactionRepository;
import org.example.walletservice.repository.WalletRepository;
import org.example.walletservice.util.SnowflakeIdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
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
        log.info("DEBIT walletId={} amount={} sagaId={} step={}", walletId, amount, sagaId, stepName);
        Wallet wallet = walletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found: " + walletId));
        if (wallet.getBalance().compareTo(amount) < 0) {
            log.warn("Insufficient balance walletId={} balance={} requested={}", walletId, wallet.getBalance(), amount);
            throw new IllegalStateException("Insufficient balance");
        }
        BigDecimal newBalance = wallet.getBalance().subtract(amount);
        walletRepository.updateBalance(walletId, newBalance);
        transactionRepository.save(Transaction.builder()
                .transactionId(idGenerator.nextId())
                .type(TransactionType.DEBIT)
                .walletId(walletId)
                .accountId(wallet.getAccountId())
                .amount(amount)
                .sagaId(sagaId)
                .idempotencyKey(sagaId + ":" + stepName + ":FORWARD")
                .build());
        log.info("DEBIT complete walletId={} newBalance={}", walletId, newBalance);
    }

    @Transactional
    public void credit(Long walletId, BigDecimal amount, long sagaId, String stepName) {
        log.info("CREDIT walletId={} amount={} sagaId={} step={}", walletId, amount, sagaId, stepName);
        Wallet wallet = walletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found: " + walletId));
        BigDecimal newBalance = wallet.getBalance().add(amount);
        walletRepository.updateBalance(walletId, newBalance);
        transactionRepository.save(Transaction.builder()
                .transactionId(idGenerator.nextId())
                .type(TransactionType.CREDIT)
                .walletId(walletId)
                .accountId(wallet.getAccountId())
                .amount(amount)
                .sagaId(sagaId)
                .idempotencyKey(sagaId + ":" + stepName + ":FORWARD")
                .build());
        log.info("CREDIT complete walletId={} newBalance={}", walletId, newBalance);
    }
}
