package org.example.walletservice.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.walletservice.models.enums.AccountStatus;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Account {
    private String id;
    private String userName;
    private String email;
    private String contactNumber;
    @Builder.Default
    private AccountStatus status = AccountStatus.ACTIVE;
    private Instant createdAt;
    private Instant updatedAt;
}
