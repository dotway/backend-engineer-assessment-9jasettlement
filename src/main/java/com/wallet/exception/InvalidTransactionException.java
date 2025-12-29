package com.wallet.exception;

/**
 * Exception thrown when a transaction request is invalid.
 */
public class InvalidTransactionException extends RuntimeException {

    public InvalidTransactionException(String message) {
        super(message);
    }
}

