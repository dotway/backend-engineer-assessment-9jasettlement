package com.wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for transfer operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferResponse {

    /**
     * Unique ID linking both sides of the transfer
     */
    private UUID transferId;

    /**
     * Transaction details for the sender (TRANSFER_OUT)
     */
    private TransactionResponse senderTransaction;

    /**
     * Transaction details for the receiver (TRANSFER_IN)
     */
    private TransactionResponse receiverTransaction;

    /**
     * Amount transferred in minor units
     */
    private Long amount;

    /**
     * Formatted amount for display
     */
    private String formattedAmount;

    private String idempotencyKey;
    private LocalDateTime createdAt;
    
    /**
     * Whether this was a duplicate request (idempotency hit)
     */
    private boolean duplicate;
}

