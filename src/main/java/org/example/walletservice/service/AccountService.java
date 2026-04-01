package org.example.walletservice.service;

import lombok.extern.slf4j.Slf4j;
import org.example.walletservice.models.Account;
import org.example.walletservice.repository.AccountRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Slf4j
@Service
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public Account createAccount(String userName, String email, String contactNumber) {
        String id = generateId(userName, contactNumber);
        log.info("Creating account id={} userName={}", id, userName);
        Account account = Account.builder()
                .id(id)
                .userName(userName)
                .email(email)
                .contactNumber(contactNumber)
                .build();
        accountRepository.save(account);
        log.info("Account created id={}", id);
        return account;
    }

    public Account getAccount(String id) {
        log.debug("Fetching account id={}", id);
        return accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + id));
    }

    public Account getByContactNumber(String contactNumber) {
        log.debug("Fetching account by contact={}", contactNumber);
        return accountRepository.findByContactNumber(contactNumber)
                .orElseThrow(() -> new IllegalArgumentException("Account not found for contact: " + contactNumber));
    }

    private String generateId(String userName, String contactNumber) {
        try {
            String input = userName.trim().toLowerCase() + ":" + contactNumber.trim();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
