package org.example.walletservice.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.walletservice.models.enums.SagaStatus;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaInstance {
    private Long id;
    private String sagaType;
    @Builder.Default
    private SagaStatus status = SagaStatus.STARTED;
    @Builder.Default
    private int currentStep = 0;
    private int totalSteps;
    private String payload;
    private String ownerInstanceId;
    private Instant lastHeartbeat;
    @Builder.Default
    private int retryCount = 0;
    @Builder.Default
    private int maxRetries = 3;
    private String errorMessage;
    private String initiatedBy;
    private Instant expiresAt;
    private Instant createdAt;
    private Instant updatedAt;
}
