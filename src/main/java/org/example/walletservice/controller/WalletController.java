package org.example.walletservice.controller;

import org.example.walletservice.dto.CreateWalletRequest;
import org.example.walletservice.models.Wallet;
import org.example.walletservice.models.enums.WalletType;
import org.example.walletservice.service.WalletService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/wallets")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping
    public ResponseEntity<Wallet> create(@RequestBody CreateWalletRequest req) {
        return ResponseEntity.ok(walletService.createWallet(req.accountId(), WalletType.valueOf(req.type()), req.currency()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Wallet> getById(@PathVariable Long id) {
        return ResponseEntity.ok(walletService.getWallet(id));
    }

    @GetMapping("/{id}/balance")
    public ResponseEntity<BigDecimal> getBalance(@PathVariable Long id) {
        return ResponseEntity.ok(walletService.getBalance(id));
    }

    @GetMapping("/account/{accountId}")
    public ResponseEntity<List<Wallet>> getByAccountId(@PathVariable String accountId) {
        return ResponseEntity.ok(walletService.getWalletsByAccountId(accountId));
    }
}
