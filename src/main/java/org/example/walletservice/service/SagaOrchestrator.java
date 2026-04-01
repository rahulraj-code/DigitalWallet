package org.example.walletservice.service;

import org.example.walletservice.models.SagaInstance;
import org.example.walletservice.models.SagaStepExecution;
import org.example.walletservice.models.enums.SagaStatus;
import org.example.walletservice.models.enums.StepStatus;
import org.example.walletservice.repository.SagaInstanceRepository;
import org.example.walletservice.repository.SagaStepExecutionRepository;
import org.example.walletservice.util.SnowflakeIdGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class SagaOrchestrator {

    private final WalletOperationService walletOps;
    private final SagaInstanceRepository sagaInstanceRepository;
    private final SagaStepExecutionRepository sagaStepRepository;
    private final SnowflakeIdGenerator idGenerator;
    private final String instanceId;

    public SagaOrchestrator(WalletOperationService walletOps,
                            SagaInstanceRepository sagaInstanceRepository,
                            SagaStepExecutionRepository sagaStepRepository,
                            SnowflakeIdGenerator idGenerator,
                            @Value("${app.instance-id}") String instanceId) {
        this.walletOps = walletOps;
        this.sagaInstanceRepository = sagaInstanceRepository;
        this.sagaStepRepository = sagaStepRepository;
        this.idGenerator = idGenerator;
        this.instanceId = instanceId;
    }

    public SagaInstance transfer(Long sourceWalletId, Long destWalletId, BigDecimal amount, String initiatedBy) {
        long sagaId = idGenerator.nextId();
        String payload = """
            {"sourceWalletId":%d,"destWalletId":%d,"amount":"%s"}
            """.formatted(sourceWalletId, destWalletId, amount.toPlainString()).trim();

        SagaInstance saga = SagaInstance.builder()
                .id(sagaId)
                .sagaType("P2P_TRANSFER")
                .totalSteps(2)
                .payload(payload)
                .ownerInstanceId(instanceId)
                .initiatedBy(initiatedBy)
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        sagaInstanceRepository.save(saga);

        // Step 1: Debit source
        sagaInstanceRepository.updateStatus(sagaId, SagaStatus.RUNNING, 1, null);
        SagaStepExecution debitStep = createStep(sagaId, 0, "DEBIT_SOURCE", "FORWARD", initiatedBy);
        try {
            walletOps.debit(sourceWalletId, amount, sagaId, "DEBIT_SOURCE");
            sagaStepRepository.updateStatus(debitStep.getId(), StepStatus.COMPLETED, null, null);
        } catch (Exception e) {
            sagaStepRepository.updateStatus(debitStep.getId(), StepStatus.FAILED, null, e.getMessage());
            sagaInstanceRepository.updateStatus(sagaId, SagaStatus.FAILED, 1, e.getMessage());
            return sagaInstanceRepository.findById(sagaId).orElseThrow();
        }

        // Step 2: Credit destination
        sagaInstanceRepository.updateStatus(sagaId, SagaStatus.RUNNING, 2, null);
        SagaStepExecution creditStep = createStep(sagaId, 1, "CREDIT_DEST", "FORWARD", initiatedBy);
        try {
            walletOps.credit(destWalletId, amount, sagaId, "CREDIT_DEST");
            sagaStepRepository.updateStatus(creditStep.getId(), StepStatus.COMPLETED, null, null);
            sagaInstanceRepository.updateStatus(sagaId, SagaStatus.COMPLETED, 2, null);
        } catch (Exception e) {
            sagaStepRepository.updateStatus(creditStep.getId(), StepStatus.FAILED, null, e.getMessage());
            compensateDebit(sourceWalletId, amount, sagaId, initiatedBy);
            sagaInstanceRepository.updateStatus(sagaId, SagaStatus.COMPENSATED, 2, e.getMessage());
        }

        return sagaInstanceRepository.findById(sagaId).orElseThrow();
    }

    private void compensateDebit(Long walletId, BigDecimal amount, long sagaId, String initiatedBy) {
        SagaStepExecution compStep = createStep(sagaId, 0, "DEBIT_SOURCE", "COMPENSATE", initiatedBy);
        try {
            walletOps.credit(walletId, amount, sagaId, "DEBIT_SOURCE_COMPENSATE");
            sagaStepRepository.updateStatus(compStep.getId(), StepStatus.COMPENSATED, null, null);
        } catch (Exception e) {
            sagaStepRepository.updateStatus(compStep.getId(), StepStatus.FAILED, null, e.getMessage());
            sagaInstanceRepository.updateStatus(sagaId, SagaStatus.POISON, 0, "Compensation failed: " + e.getMessage());
        }
    }

    private SagaStepExecution createStep(long sagaId, int index, String name, String type, String initiatedBy) {
        SagaStepExecution step = SagaStepExecution.builder()
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
