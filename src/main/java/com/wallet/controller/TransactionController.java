package com.wallet.controller;

import com.wallet.dto.TransactionRequest;
import com.wallet.dto.TransactionResponse;
import com.wallet.dto.TransferRequest;
import com.wallet.dto.TransferResponse;
import com.wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for transaction operations.
 * 
 * Endpoints:
 * - POST /transactions - Credit or debit a wallet
 * - POST /transactions/transfer - Transfer between two wallets
 */
@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
@Slf4j
public class TransactionController {

    private final WalletService walletService;

    /**
     * Process a credit or debit transaction.
     *
     * Rules:
     * - Debits must not make the balance negative
     * - Re-using the same idempotency_key will not apply the transaction twice
     *
     * @param request the transaction request
     * @return the transaction result with HTTP 201 (new) or 200 (duplicate)
     */
    @PostMapping
    public ResponseEntity<TransactionResponse> processTransaction(
            @Valid @RequestBody TransactionRequest request) {
        
        log.info("POST /transactions - Type: {}, Wallet: {}", 
                request.getType(), request.getWalletId());
        
        TransactionResponse response = walletService.processTransaction(request);
        
        // Return 200 for duplicate (idempotent) requests, 201 for new transactions
        HttpStatus status = response.isDuplicate() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(response);
    }

    /**
     * Transfer funds between two wallets atomically.
     *
     * This operation debits the sender and credits the receiver in a single
     * atomic transaction. If either operation fails, the entire transfer
     * is rolled back.
     *
     * @param request the transfer request
     * @return the transfer result with both transaction details
     */
    @PostMapping("/transfer")
    public ResponseEntity<TransferResponse> processTransfer(
            @Valid @RequestBody TransferRequest request) {
        
        log.info("POST /transactions/transfer - From: {}, To: {}", 
                request.getFromWalletId(), request.getToWalletId());
        
        TransferResponse response = walletService.processTransfer(request);
        
        // Return 200 for duplicate (idempotent) requests, 201 for new transfers
        HttpStatus status = response.isDuplicate() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(response);
    }
}

