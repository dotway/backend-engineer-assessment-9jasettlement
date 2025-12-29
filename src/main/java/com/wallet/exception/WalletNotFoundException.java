package com.wallet.exception;

import java.util.UUID;

/**
 * Exception thrown when a wallet is not found.
 */
public class WalletNotFoundException extends RuntimeException {

    public WalletNotFoundException(UUID walletId) {
        super("Wallet not found with ID: " + walletId);
    }

    public WalletNotFoundException(String message) {
        super(message);
    }
}

