package org.example.walletservice.controller;

import org.example.walletservice.dto.TransferRequest;
import org.example.walletservice.models.Transaction;
import org.example.walletservice.models.TransactionIntent;
import org.example.walletservice.repository.TransactionIntentRepository;
import org.example.walletservice.service.SagaOrchestrator;
import org.example.walletservice.service.TransactionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final SagaOrchestrator sagaOrchestrator;
    private final TransactionService transactionService;
    private final TransactionIntentRepository intentRepository;

    public TransactionController(SagaOrchestrator sagaOrchestrator,
                                 TransactionService transactionService,
                                 TransactionIntentRepository intentRepository) {
        this.sagaOrchestrator = sagaOrchestrator;
        this.transactionService = transactionService;
        this.intentRepository = intentRepository;
    }

    @PostMapping("/transfer")
    public ResponseEntity<TransactionIntent> transfer(@RequestBody TransferRequest req) {
        TransactionIntent intent = sagaOrchestrator.createIntent(
                req.idempotencyKey(), req.sourceWalletId(), req.destWalletId(), req.amount(), req.initiatedBy());
        if (intent == null) {
            // Duplicate — return the existing intent
            return intentRepository.findById(req.idempotencyKey())
                    .map(existing -> ResponseEntity.ok(existing))
                    .orElse(ResponseEntity.notFound().build());
        }
        sagaOrchestrator.executeTransfer(intent);
        return ResponseEntity.accepted().body(intent);
    }

    @GetMapping("/transfer/{intentId}/status")
    public ResponseEntity<TransactionIntent> getTransferStatus(@PathVariable String intentId) {
        return intentRepository.findById(intentId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/wallet/{walletId}")
    public ResponseEntity<List<Transaction>> getByWalletId(@PathVariable Long walletId) {
        return ResponseEntity.ok(transactionService.getTransactionsByWalletId(walletId));
    }

    @GetMapping("/saga/{sagaId}")
    public ResponseEntity<List<Transaction>> getBySagaId(@PathVariable Long sagaId) {
        return ResponseEntity.ok(transactionService.getTransactionsBySagaId(sagaId));
    }
}
