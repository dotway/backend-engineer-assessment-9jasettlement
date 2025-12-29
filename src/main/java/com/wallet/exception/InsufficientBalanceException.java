package com.wallet.exception;

/**
 * Exception thrown when a debit operation would result in a negative balance.
 */
public class InsufficientBalanceException extends RuntimeException {

    public InsufficientBalanceException(Long currentBalance, Long requestedAmount) {
        super(String.format(
            "Insufficient balance. Current: %d, Requested: %d",
            currentBalance, requestedAmount
        ));
    }

    public InsufficientBalanceException(String message) {
        super(message);
    }
}

