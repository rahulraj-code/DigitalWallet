package org.example.walletservice.service;

import lombok.extern.slf4j.Slf4j;
import org.example.walletservice.models.Wallet;
import org.example.walletservice.models.enums.WalletType;
import org.example.walletservice.repository.WalletRepository;
import org.example.walletservice.util.SnowflakeIdGenerator;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
public class WalletService {

    private final WalletRepository walletRepository;
    private final SnowflakeIdGenerator idGenerator;

    public WalletService(WalletRepository walletRepository, SnowflakeIdGenerator idGenerator) {
        this.walletRepository = walletRepository;
        this.idGenerator = idGenerator;
    }

    public Wallet createWallet(String accountId, WalletType type, String currency) {
        long id = idGenerator.nextId();
        log.info("Creating wallet id={} accountId={} type={} currency={}", id, accountId, type, currency);
        Wallet wallet = Wallet.builder()
                .id(id)
                .accountId(accountId)
                .type(type)
                .currency(currency)
                .build();
        walletRepository.save(wallet);
        log.info("Wallet created id={}", id);
        return wallet;
    }

    public Wallet getWallet(Long id) {
        log.debug("Fetching wallet id={}", id);
        return walletRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found: " + id));
    }

    public List<Wallet> getWalletsByAccountId(String accountId) {
        return walletRepository.findByAccountId(accountId);
    }

    public BigDecimal getBalance(Long walletId) {
        return getWallet(walletId).getBalance();
    }
}
