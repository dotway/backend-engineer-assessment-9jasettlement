package com.wallet.dto;

import com.wallet.constants.WalletConstants;
import com.wallet.entity.Transaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for transaction information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {

    private UUID id;
    private UUID walletId;
    private String type;
    
    /**
     * Amount in minor units
     */
    private Long amount;
    
    /**
     * Formatted amount for display (e.g., "10.50")
     */
    private String formattedAmount;
    
    /**
     * Balance after this transaction
     */
    private Long balanceAfter;
    
    /**
     * Formatted balance after for display
     */
    private String formattedBalanceAfter;
    
    private String idempotencyKey;
    private String description;
    
    /**
     * For transfers: the other wallet involved
     */
    private UUID relatedWalletId;
    
    /**
     * For transfers: links both transaction records
     */
    private UUID transferId;
    
    private LocalDateTime createdAt;
    
    /**
     * Whether this was a duplicate request (idempotency hit)
     */
    private boolean duplicate;

    /**
     * Create a TransactionResponse from a Transaction entity.
     */
    public static TransactionResponse fromEntity(Transaction transaction, boolean isDuplicate) {
        return TransactionResponse.builder()
                .id(transaction.getId())
                .walletId(transaction.getWallet().getId())
                .type(transaction.getType().name())
                .amount(transaction.getAmount())
                .formattedAmount(formatAmount(transaction.getAmount()))
                .balanceAfter(transaction.getBalanceAfter())
                .formattedBalanceAfter(formatAmount(transaction.getBalanceAfter()))
                .idempotencyKey(transaction.getIdempotencyKey())
                .description(transaction.getDescription())
                .relatedWalletId(transaction.getRelatedWalletId())
                .transferId(transaction.getTransferId())
                .createdAt(transaction.getCreatedAt())
                .duplicate(isDuplicate)
                .build();
    }

    /**
     * Format amount from minor units to display format.
     */
    private static String formatAmount(Long amount) {
        if (amount == null) return "0.00";
        long wholePart = amount / WalletConstants.MINOR_UNITS_DIVISOR;
        long decimalPart = Math.abs(amount % WalletConstants.MINOR_UNITS_DIVISOR);
        return String.format("%d.%02d", wholePart, decimalPart);
    }
}
