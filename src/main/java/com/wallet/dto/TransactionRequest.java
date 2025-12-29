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
 * Request DTO for creating a credit or debit transaction.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionRequest {

    /**
     * The wallet ID to credit or debit
     */
    @NotNull(message = "Wallet ID is required")
    private UUID walletId;

    /**
     * Type of transaction: "CREDIT" or "DEBIT"
     */
    @NotBlank(message = "Transaction type is required")
    private String type;

    /**
     * Amount in minor units (must be positive).
     * E.g., 1050 = $10.50
     */
    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private Long amount;

    /**
     * Unique key to ensure idempotency.
     * Same key = same transaction (won't be processed twice)
     */
    @NotBlank(message = "Idempotency key is required")
    private String idempotencyKey;

    /**
     * Optional description for the transaction
     */
    private String description;
}

