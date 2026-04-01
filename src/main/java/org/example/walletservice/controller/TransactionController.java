package org.example.walletservice.controller;

import org.example.walletservice.dto.TransferRequest;
import org.example.walletservice.models.SagaInstance;
import org.example.walletservice.models.Transaction;
import org.example.walletservice.service.SagaOrchestrator;
import org.example.walletservice.service.TransactionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final SagaOrchestrator sagaOrchestrator;

    public TransactionController(TransactionService transactionService, SagaOrchestrator sagaOrchestrator) {
        this.transactionService = transactionService;
        this.sagaOrchestrator = sagaOrchestrator;
    }

    @GetMapping("/saga/{sagaId}")
    public ResponseEntity<List<Transaction>> getTransactionsBySagaId(@PathVariable Long sagaId) {
        return ResponseEntity.ok(transactionService.getTransactionsBySagaId(sagaId));
    }

    @GetMapping("/wallet/{walletId}")
    public ResponseEntity<List<Transaction>> getTransactionsByWalletId(@PathVariable Long walletId) {
        return ResponseEntity.ok(transactionService.getTransactionsByWalletId(walletId));
    }

    @PostMapping("/transfer")
    public ResponseEntity<SagaInstance> transfer(@RequestBody TransferRequest request) {
        return ResponseEntity.ok(sagaOrchestrator.transfer(
                request.sourceWalletId(), request.destWalletId(), request.amount(), request.initiatedBy()));
    }
}
