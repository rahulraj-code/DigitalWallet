package org.example.walletservice.service;

import org.example.walletservice.models.Transaction;
import org.example.walletservice.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;

    public TransactionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public List<Transaction> getTransactionsByWalletId(Long walletId) {
        return transactionRepository.findByWalletId(walletId);
    }

    public List<Transaction> getTransactionsBySagaId(Long sagaId) {
        return transactionRepository.findBySagaId(sagaId);
    }
}
