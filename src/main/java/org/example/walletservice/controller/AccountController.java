package org.example.walletservice.controller;

import org.example.walletservice.dto.CreateAccountRequest;
import org.example.walletservice.models.Account;
import org.example.walletservice.service.AccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<Account> create(@RequestBody CreateAccountRequest req) {
        return ResponseEntity.ok(accountService.createAccount(req.userName(), req.email(), req.contactNumber()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Account> getById(@PathVariable String id) {
        return ResponseEntity.ok(accountService.getAccount(id));
    }
}
