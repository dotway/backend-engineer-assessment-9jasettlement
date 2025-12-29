package com.wallet.dto;

import com.wallet.entity.Wallet;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for wallet information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletResponse {

    private UUID id;
    private String name;
    
    /**
     * Balance in minor units (e.g., cents)
     */
    private Long balance;
    
    /**
     * Formatted balance for display (e.g., "10.50")
     */
    private String formattedBalance;
    
    private String currency;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Create a WalletResponse from a Wallet entity.
     */
    public static WalletResponse fromEntity(Wallet wallet) {
        return WalletResponse.builder()
                .id(wallet.getId())
                .name(wallet.getName())
                .balance(wallet.getBalance())
                .formattedBalance(formatBalance(wallet.getBalance()))
                .currency(wallet.getCurrency())
                .createdAt(wallet.getCreatedAt())
                .updatedAt(wallet.getUpdatedAt())
                .build();
    }

    /**
     * Format balance from minor units to display format.
     * E.g., 1050 -> "10.50"
     */
    private static String formatBalance(Long balance) {
        if (balance == null) return "0.00";
        long wholePart = balance / 100;
        long decimalPart = Math.abs(balance % 100);
        return String.format("%d.%02d", wholePart, decimalPart);
    }
}

