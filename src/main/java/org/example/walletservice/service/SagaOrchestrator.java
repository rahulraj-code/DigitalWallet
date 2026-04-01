package org.example.walletservice.service;

import lombok.extern.slf4j.Slf4j;
import org.example.walletservice.models.SagaInstance;
import org.example.walletservice.models.SagaStepExecution;
import org.example.walletservice.models.TransactionIntent;
import org.example.walletservice.models.enums.IntentStatus;
import org.example.walletservice.models.enums.SagaStatus;
import org.example.walletservice.models.enums.StepStatus;
import org.example.walletservice.repository.SagaInstanceRepository;
import org.example.walletservice.repository.SagaStepExecutionRepository;
import org.example.walletservice.repository.TransactionIntentRepository;
import org.example.walletservice.util.SnowflakeIdGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;

@Slf4j
@Service
public class SagaOrchestrator {

    private static final int MAX_RETRIES = 3;
    private static final long BASE_DELAY_MS = 200;

    private final WalletOperationService walletOps;
    private final SagaInstanceRepository sagaInstanceRepository;
    private final SagaStepExecutionRepository sagaStepRepository;
    private final TransactionIntentRepository intentRepository;
    private final SnowflakeIdGenerator idGenerator;
    private final String instanceId;

    public SagaOrchestrator(WalletOperationService walletOps,
                            SagaInstanceRepository sagaInstanceRepository,
                            SagaStepExecutionRepository sagaStepRepository,
                            TransactionIntentRepository intentRepository,
                            SnowflakeIdGenerator idGenerator,
                            @Value("${app.instance-id}") String instanceId) {
        this.walletOps = walletOps;
        this.sagaInstanceRepository = sagaInstanceRepository;
        this.sagaStepRepository = sagaStepRepository;
        this.intentRepository = intentRepository;
        this.idGenerator = idGenerator;
        this.instanceId = instanceId;
    }

    public TransactionIntent createIntent(Long sourceWalletId, Long destWalletId,
                                          BigDecimal amount, String initiatedBy) {
        var intent = TransactionIntent.builder()
                .intentId(String.valueOf(idGenerator.nextId()))
                .sourceWalletId(sourceWalletId)
                .destWalletId(destWalletId)
                .amount(amount)
                .initiatedBy(initiatedBy)
                .build();
        intentRepository.save(intent);
        log.info("Intent created intentId={} source={} dest={} amount={}", intent.getIntentId(), sourceWalletId, destWalletId, amount);
        return intent;
    }

    @Async
    public void executeTransfer(TransactionIntent intent) {
        long sagaId = idGenerator.nextId();
        try {
            doExecuteTransfer(intent, sagaId);
        } catch (Exception e) {
            log.error("SAGA sagaId={} interrupted/unexpected: {}", sagaId, e.getMessage());

            try{
                sagaInstanceRepository.updateStatus(sagaId,SagaStatus.FAILED, 0, e.getMessage());
            }
            catch ( Exception ignored){
                log.warn("could not update SAGA status - SAGA instane may not exist");
            }
            intentRepository.updateStatus(intent.getIntentId(), IntentStatus.FAILED, null, e.getMessage());
        }
    }

    private void doExecuteTransfer(TransactionIntent intent, long sagaId) {
        log.info("Starting SAGA sagaId={} intentId={}", sagaId, intent.getIntentId());

        String payload = """
            {"intentId":"%s","sourceWalletId":%d,"destWalletId":%d,"amount":"%s"}
            """.formatted(intent.getIntentId(), intent.getSourceWalletId(),
                intent.getDestWalletId(), intent.getAmount().toPlainString()).trim();

        var saga = SagaInstance.builder()
                .id(sagaId)
                .sagaType("P2P_TRANSFER")
                .totalSteps(2)
                .payload(payload)
                .ownerInstanceId(instanceId)
                .initiatedBy(intent.getInitiatedBy())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        sagaInstanceRepository.save(saga);
        intentRepository.updateStatus(intent.getIntentId(), IntentStatus.PROCESSING, sagaId, null);

        // Step 1: Debit source
        log.info("SAGA sagaId={} step=1 DEBIT_SOURCE", sagaId);
        sagaInstanceRepository.updateStatus(sagaId, SagaStatus.RUNNING, 1, null);
        var debitStep = createStep(sagaId, 0, "DEBIT_SOURCE", "FORWARD", intent.getInitiatedBy());
        try {
            executeWithRetry(() -> walletOps.debit(intent.getSourceWalletId(), intent.getAmount(), sagaId, "DEBIT_SOURCE"),
                    "DEBIT_SOURCE", sagaId);
            sagaStepRepository.updateStatus(debitStep.getId(), StepStatus.COMPLETED, null, null);
            log.info("SAGA sagaId={} step=1 DEBIT_SOURCE COMPLETED", sagaId);
        } catch (Exception e) {
            log.error("SAGA sagaId={} step=1 DEBIT_SOURCE FAILED: {}", sagaId, e.getMessage());
            sagaStepRepository.updateStatus(debitStep.getId(), StepStatus.FAILED, null, e.getMessage());
            sagaInstanceRepository.updateStatus(sagaId, SagaStatus.FAILED, 1, e.getMessage());
            intentRepository.updateStatus(intent.getIntentId(), IntentStatus.FAILED, sagaId, e.getMessage());
            return;
        }

        // Step 2: Credit destination
        log.info("SAGA sagaId={} step=2 CREDIT_DEST", sagaId);
        sagaInstanceRepository.updateStatus(sagaId, SagaStatus.RUNNING, 2, null);
        var creditStep = createStep(sagaId, 1, "CREDIT_DEST", "FORWARD", intent.getInitiatedBy());
        try {
            executeWithRetry(() -> walletOps.credit(intent.getDestWalletId(), intent.getAmount(), sagaId, "CREDIT_DEST"),
                    "CREDIT_DEST", sagaId);
            sagaStepRepository.updateStatus(creditStep.getId(), StepStatus.COMPLETED, null, null);
            sagaInstanceRepository.updateStatus(sagaId, SagaStatus.COMPLETED, 2, null);
            intentRepository.updateStatus(intent.getIntentId(), IntentStatus.COMPLETED, sagaId, null);
            log.info("SAGA sagaId={} COMPLETED intentId={}", sagaId, intent.getIntentId());
        } catch (Exception e) {
            log.error("SAGA sagaId={} step=2 CREDIT_DEST FAILED: {}", sagaId, e.getMessage());
            sagaStepRepository.updateStatus(creditStep.getId(), StepStatus.FAILED, null, e.getMessage());
            compensateDebit(intent.getSourceWalletId(), intent.getAmount(), sagaId, intent.getInitiatedBy());
            var finalSaga = sagaInstanceRepository.findById(sagaId).orElseThrow();
            IntentStatus intentStatus = finalSaga.getStatus() == SagaStatus.POISON ? IntentStatus.FAILED : IntentStatus.COMPENSATED;
            intentRepository.updateStatus(intent.getIntentId(), intentStatus, sagaId, e.getMessage());
        }
    }

    private void compensateDebit(Long walletId, BigDecimal amount, long sagaId, String initiatedBy) {
        log.warn("COMPENSATING sagaId={} DEBIT_SOURCE walletId={}", sagaId, walletId);
        var compStep = createStep(sagaId, 0, "DEBIT_SOURCE", "COMPENSATE", initiatedBy);
        try {
            executeWithRetry(() -> walletOps.credit(walletId, amount, sagaId, "DEBIT_SOURCE_COMPENSATE"),
                    "DEBIT_SOURCE_COMPENSATE", sagaId);
            sagaStepRepository.updateStatus(compStep.getId(), StepStatus.COMPENSATED, null, null);
            sagaInstanceRepository.updateStatus(sagaId, SagaStatus.COMPENSATED, 2, null);
            log.info("COMPENSATION complete sagaId={}", sagaId);
        } catch (Exception e) {
            log.error("COMPENSATION FAILED sagaId={} → POISON: {}", sagaId, e.getMessage());
            sagaStepRepository.updateStatus(compStep.getId(), StepStatus.FAILED, null, e.getMessage());
            sagaInstanceRepository.updateStatus(sagaId, SagaStatus.POISON, 0, "Compensation failed: " + e.getMessage());
        }
    }

    /**
     * Retries only on transient failures (DataAccessException).
     * Business errors (IllegalStateException, IllegalArgumentException) fail immediately.
     */
    private void executeWithRetry(Runnable operation, String stepName, long sagaId) {
        int attempt = 0;
        while (true) {
            try {
                operation.run();
                return;
            } catch (DataAccessException e) {
                attempt++;
                if (attempt >= MAX_RETRIES) {
                    log.error("SAGA sagaId={} step={} exhausted {} retries: {}", sagaId, stepName, MAX_RETRIES, e.getMessage());
                    throw e;
                }
                long delay = BASE_DELAY_MS * (1L << (attempt - 1)); // exponential: 200, 400, 800...
                log.warn("SAGA sagaId={} step={} retry {}/{} after {}ms: {}", sagaId, stepName, attempt, MAX_RETRIES, delay, e.getMessage());
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry interrupted", ie);
                }
            }
        }
    }

    private SagaStepExecution createStep(long sagaId, int index, String name, String type, String initiatedBy) {
        var step = SagaStepExecution.builder()
                .id(idGenerator.nextId())
                .sagaId(sagaId)
                .stepIndex(index)
                .stepName(name)
                .stepType(type)
                .initiatedBy(initiatedBy)
                .build();
        sagaStepRepository.save(step);
        sagaStepRepository.markStarted(step.getId());
        return step;
    }
}
