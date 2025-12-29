package com.wallet.service;

import com.wallet.dto.*;

import java.util.UUID;

/**
 * Service interface for wallet operations.
 */
public interface WalletService {

    WalletResponse createWallet(CreateWalletRequest request);

    WalletResponse getWallet(UUID walletId);

    TransactionResponse processTransaction(TransactionRequest request);

    TransferResponse processTransfer(TransferRequest request);
}
