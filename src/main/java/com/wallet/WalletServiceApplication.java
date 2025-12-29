package com.wallet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the Wallet Service application.
 * 
 * This service provides REST APIs for:
 * - Creating wallets
 * - Credit/Debit transactions with idempotency
 * - Atomic transfers between wallets
 * - Retrieving wallet details
 */
@SpringBootApplication
public class WalletServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WalletServiceApplication.class, args);
    }
}

