package com.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request DTO for transferring funds between two wallets.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferRequest {

    /**
     * The wallet ID to transfer FROM (sender)
     */
    @NotNull(message = "Sender wallet ID is required")
    private UUID fromWalletId;

    /**
     * The wallet ID to transfer TO (receiver)
     */
    @NotNull(message = "Receiver wallet ID is required")
    private UUID toWalletId;

    /**
     * Amount to transfer in minor units (must be positive).
     * E.g., 1050 = $10.50
     */
    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private Long amount;

    /**
     * Unique key to ensure idempotency.
     * Same key = same transfer (won't be processed twice)
     */
    @NotBlank(message = "Idempotency key is required")
    private String idempotencyKey;

    /**
     * Optional description for the transfer
     */
    private String description;
}

