package org.example.walletservice.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.walletservice.models.enums.StepStatus;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaStepExecution {
    private Long id;
    private Long sagaId;
    private int stepIndex;
    private String stepName;
    private String stepType;
    @Builder.Default
    private StepStatus status = StepStatus.PENDING;
    private String inputData;
    private String outputData;
    private String errorDetail;
    private String initiatedBy;
    private Instant startedAt;
    private Instant completedAt;
    private Instant createdAt;
}
