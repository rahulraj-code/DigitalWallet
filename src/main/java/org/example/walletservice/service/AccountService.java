package org.example.walletservice.service;

import org.example.walletservice.models.Account;
import org.example.walletservice.repository.AccountRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public Account createAccount(String userName, String email, String contactNumber) {
        String id = generateId(userName, contactNumber);

        Account account = Account.builder()
                .id(id)
                .userName(userName)
                .email(email)
                .contactNumber(contactNumber)
                .build();
        accountRepository.save(account);
        return account;
    }

    public Account getAccount(String id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + id));
    }

    public Account getByContactNumber(String contactNumber) {
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
