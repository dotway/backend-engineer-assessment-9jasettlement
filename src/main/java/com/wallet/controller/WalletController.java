package com.wallet.controller;

import com.wallet.dto.CreateWalletRequest;
import com.wallet.dto.WalletResponse;
import com.wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/wallets")
@RequiredArgsConstructor
@Slf4j
public class WalletController {

    private final WalletService walletService;

    /**
     * Create a new wallet.
     *
     * @param request the wallet creation request containing an optional name,
     *                currency (default USD), and initial balance (default 0)
     * @return the created wallet with HTTP 201
     */
    @PostMapping
    public ResponseEntity<WalletResponse> createWallet(
            @Valid @RequestBody(required = false) CreateWalletRequest request) {
        
        // Handle null request body
        if (request == null) {
            request = new CreateWalletRequest();
        }
        
        log.info("POST /wallets - Creating wallet");
        WalletResponse response = walletService.createWallet(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get wallet details by ID.
     *
     * @param id the wallet UUID
     * @return the wallet details
     */
    @GetMapping("/{id}")
    public ResponseEntity<WalletResponse> getWallet(@PathVariable UUID id) {
        log.info("GET /wallets/{}", id);
        WalletResponse response = walletService.getWallet(id);
        return ResponseEntity.ok(response);
    }
}

