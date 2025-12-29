package com.wallet.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a new wallet.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateWalletRequest {

    /**
     * Optional name for the wallet
     */
    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;

    /**
     * Currency code (e.g., "USD", "EUR").
     * Defaults to "USD" if not provided.
     */
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter uppercase code")
    private String currency = "USD";

    /**
     * Initial balance in minor units (e.g., cents).
     * Defaults to 0 if not provided.
     */
    private Long initialBalance;
}

