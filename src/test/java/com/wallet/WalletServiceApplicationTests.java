package com.wallet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the Wallet Service.
 */
@SpringBootTest
@AutoConfigureMockMvc
class WalletServiceApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void contextLoads() {
    }

    @Test
    void testCreateWallet() throws Exception {
        CreateWalletRequest request = CreateWalletRequest.builder()
                .name("Test Wallet")
                .currency("USD")
                .initialBalance(10000L)
                .build();

        mockMvc.perform(post("/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Test Wallet"))
                .andExpect(jsonPath("$.balance").value(10000))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.formattedBalance").value("100.00"));
    }

    @Test
    void testGetWallet() throws Exception {
        // Create wallet first
        CreateWalletRequest createRequest = CreateWalletRequest.builder()
                .name("Get Test Wallet")
                .currency("USD")
                .initialBalance(5000L)
                .build();

        MvcResult createResult = mockMvc.perform(post("/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        WalletResponse createdWallet = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), WalletResponse.class);

        // Get the wallet
        mockMvc.perform(get("/wallets/" + createdWallet.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(createdWallet.getId().toString()))
                .andExpect(jsonPath("$.name").value("Get Test Wallet"))
                .andExpect(jsonPath("$.balance").value(5000));
    }

    @Test
    void testCreditTransaction() throws Exception {
        // Create wallet
        WalletResponse wallet = createTestWallet(10000L);

        // Credit transaction
        TransactionRequest creditRequest = TransactionRequest.builder()
                .walletId(wallet.getId())
                .type("CREDIT")
                .amount(5000L)
                .idempotencyKey("test-credit-" + UUID.randomUUID())
                .description("Test credit")
                .build();

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(creditRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("CREDIT"))
                .andExpect(jsonPath("$.amount").value(5000))
                .andExpect(jsonPath("$.balanceAfter").value(15000))
                .andExpect(jsonPath("$.duplicate").value(false));
    }

    @Test
    void testDebitTransaction() throws Exception {
        // Create wallet
        WalletResponse wallet = createTestWallet(10000L);

        // Debit transaction
        TransactionRequest debitRequest = TransactionRequest.builder()
                .walletId(wallet.getId())
                .type("DEBIT")
                .amount(3000L)
                .idempotencyKey("test-debit-" + UUID.randomUUID())
                .description("Test debit")
                .build();

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(debitRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("DEBIT"))
                .andExpect(jsonPath("$.amount").value(3000))
                .andExpect(jsonPath("$.balanceAfter").value(7000));
    }

    @Test
    void testDebitInsufficientBalance() throws Exception {
        // Create wallet with low balance
        WalletResponse wallet = createTestWallet(1000L);

        // Try to debit more than balance
        TransactionRequest debitRequest = TransactionRequest.builder()
                .walletId(wallet.getId())
                .type("DEBIT")
                .amount(5000L)
                .idempotencyKey("test-insufficient-" + UUID.randomUUID())
                .build();

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(debitRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"));
    }

    @Test
    void testIdempotency() throws Exception {
        // Create wallet
        WalletResponse wallet = createTestWallet(10000L);
        String idempotencyKey = "test-idempotency-" + UUID.randomUUID();

        // First credit
        TransactionRequest creditRequest = TransactionRequest.builder()
                .walletId(wallet.getId())
                .type("CREDIT")
                .amount(5000L)
                .idempotencyKey(idempotencyKey)
                .build();

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(creditRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.duplicate").value(false));

        // Duplicate credit (same idempotency key)
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(creditRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true));

        // Verify balance only increased once
        mockMvc.perform(get("/wallets/" + wallet.getId()))
                .andExpect(jsonPath("$.balance").value(15000));
    }

    @Test
    void testTransfer() throws Exception {
        // Create two wallets
        WalletResponse senderWallet = createTestWallet(10000L);
        WalletResponse receiverWallet = createTestWallet(5000L);

        // Transfer
        TransferRequest transferRequest = TransferRequest.builder()
                .fromWalletId(senderWallet.getId())
                .toWalletId(receiverWallet.getId())
                .amount(3000L)
                .idempotencyKey("test-transfer-" + UUID.randomUUID())
                .description("Test transfer")
                .build();

        mockMvc.perform(post("/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transferRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.senderTransaction.type").value("TRANSFER_OUT"))
                .andExpect(jsonPath("$.senderTransaction.balanceAfter").value(7000))
                .andExpect(jsonPath("$.receiverTransaction.type").value("TRANSFER_IN"))
                .andExpect(jsonPath("$.receiverTransaction.balanceAfter").value(8000))
                .andExpect(jsonPath("$.duplicate").value(false));

        // Verify balances
        mockMvc.perform(get("/wallets/" + senderWallet.getId()))
                .andExpect(jsonPath("$.balance").value(7000));

        mockMvc.perform(get("/wallets/" + receiverWallet.getId()))
                .andExpect(jsonPath("$.balance").value(8000));
    }

    @Test
    void testTransferInsufficientBalance() throws Exception {
        // Create two wallets
        WalletResponse senderWallet = createTestWallet(1000L);
        WalletResponse receiverWallet = createTestWallet(5000L);

        // Try to transfer more than sender's balance
        TransferRequest transferRequest = TransferRequest.builder()
                .fromWalletId(senderWallet.getId())
                .toWalletId(receiverWallet.getId())
                .amount(5000L)
                .idempotencyKey("test-transfer-fail-" + UUID.randomUUID())
                .build();

        mockMvc.perform(post("/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transferRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"));

        // Verify balances unchanged
        mockMvc.perform(get("/wallets/" + senderWallet.getId()))
                .andExpect(jsonPath("$.balance").value(1000));

        mockMvc.perform(get("/wallets/" + receiverWallet.getId()))
                .andExpect(jsonPath("$.balance").value(5000));
    }

    @Test
    void testTransferToSameWallet() throws Exception {
        WalletResponse wallet = createTestWallet(10000L);

        TransferRequest transferRequest = TransferRequest.builder()
                .fromWalletId(wallet.getId())
                .toWalletId(wallet.getId())
                .amount(1000L)
                .idempotencyKey("test-same-wallet-" + UUID.randomUUID())
                .build();

        mockMvc.perform(post("/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transferRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSACTION"));
    }

    @Test
    void testWalletNotFound() throws Exception {
        UUID randomId = UUID.randomUUID();
        mockMvc.perform(get("/wallets/" + randomId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    /**
     * Helper method to create a test wallet.
     */
    private WalletResponse createTestWallet(Long initialBalance) throws Exception {
        CreateWalletRequest request = CreateWalletRequest.builder()
                .name("Test Wallet " + UUID.randomUUID())
                .currency("USD")
                .initialBalance(initialBalance)
                .build();

        MvcResult result = mockMvc.perform(post("/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readValue(
                result.getResponse().getContentAsString(), WalletResponse.class);
    }
}

