package com.wallet.service;

import com.wallet.dto.*;
import com.wallet.entity.Transaction;
import com.wallet.entity.Transaction.TransactionType;
import com.wallet.entity.Wallet;
import com.wallet.exception.InsufficientBalanceException;
import com.wallet.exception.InvalidTransactionException;
import com.wallet.exception.WalletNotFoundException;
import com.wallet.repository.TransactionRepository;
import com.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Service layer for wallet operations.
 * 
 * All balance-modifying operations are transactional to ensure data consistency.
 * Idempotency is enforced using unique idempotency keys.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    /**
     * Create a new wallet.
     *
     * @param request the wallet creation request
     * @return the created wallet details
     */
    @Transactional
    public WalletResponse createWallet(CreateWalletRequest request) {
        log.info("Creating wallet with name: {}, currency: {}", request.getName(), request.getCurrency());

        Wallet wallet = Wallet.builder()
                .name(request.getName())
                .balance(request.getInitialBalance() != null ? request.getInitialBalance() : 0L)
                .currency(request.getCurrency() != null ? request.getCurrency() : "USD")
                .build();

        Wallet savedWallet = walletRepository.save(wallet);
        log.info("Created wallet with ID: {}", savedWallet.getId());

        return WalletResponse.fromEntity(savedWallet);
    }

    /**
     * Get wallet details by ID.
     *
     * @param walletId the wallet ID
     * @return the wallet details
     * @throws WalletNotFoundException if wallet not found
     */
    @Transactional(readOnly = true)
    public WalletResponse getWallet(UUID walletId) {
        log.debug("Getting wallet: {}", walletId);
        
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId));

        return WalletResponse.fromEntity(wallet);
    }

    /**
     * Process a credit or debit transaction.
     * 
     * Uses pessimistic locking to prevent concurrent modifications.
     * Idempotency is enforced via the idempotency key.
     *
     * @param request the transaction request
     * @return the transaction result
     */
    @Transactional
    public TransactionResponse processTransaction(TransactionRequest request) {
        log.info("Processing transaction - Type: {}, Wallet: {}, Amount: {}, Key: {}",
                request.getType(), request.getWalletId(), request.getAmount(), request.getIdempotencyKey());

        // Check for idempotency - return existing transaction if key exists
        Optional<Transaction> existingTransaction = 
                transactionRepository.findByIdempotencyKey(request.getIdempotencyKey());
        
        if (existingTransaction.isPresent()) {
            log.info("Duplicate transaction detected for key: {}", request.getIdempotencyKey());
            return TransactionResponse.fromEntity(existingTransaction.get(), true);
        }

        // Validate transaction type
        TransactionType type;
        try {
            type = TransactionType.valueOf(request.getType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidTransactionException(
                    "Invalid transaction type. Must be CREDIT or DEBIT");
        }

        if (type != TransactionType.CREDIT && type != TransactionType.DEBIT) {
            throw new InvalidTransactionException(
                    "Transaction type must be CREDIT or DEBIT for this endpoint");
        }

        // Get wallet with lock to prevent concurrent modifications
        Wallet wallet = walletRepository.findByIdWithLock(request.getWalletId())
                .orElseThrow(() -> new WalletNotFoundException(request.getWalletId()));

        // Calculate new balance
        Long newBalance;
        if (type == TransactionType.CREDIT) {
            newBalance = wallet.getBalance() + request.getAmount();
        } else {
            // DEBIT
            if (wallet.getBalance() < request.getAmount()) {
                throw new InsufficientBalanceException(wallet.getBalance(), request.getAmount());
            }
            newBalance = wallet.getBalance() - request.getAmount();
        }

        // Update wallet balance
        wallet.setBalance(newBalance);
        walletRepository.save(wallet);

        // Create transaction record
        Transaction transaction = Transaction.builder()
                .idempotencyKey(request.getIdempotencyKey())
                .wallet(wallet)
                .type(type)
                .amount(request.getAmount())
                .balanceAfter(newBalance)
                .description(request.getDescription())
                .build();

        Transaction savedTransaction = transactionRepository.save(transaction);
        log.info("Transaction completed - ID: {}, New Balance: {}", 
                savedTransaction.getId(), newBalance);

        return TransactionResponse.fromEntity(savedTransaction, false);
    }

    /**
     * Transfer funds between two wallets atomically.
     * 
     * Both debit and credit happen in a single transaction.
     * If either fails, the entire operation is rolled back.
     *
     * @param request the transfer request
     * @return the transfer result with both transaction details
     */
    @Transactional
    public TransferResponse processTransfer(TransferRequest request) {
        log.info("Processing transfer - From: {}, To: {}, Amount: {}, Key: {}",
                request.getFromWalletId(), request.getToWalletId(), 
                request.getAmount(), request.getIdempotencyKey());

        // Validate: cannot transfer to same wallet
        if (request.getFromWalletId().equals(request.getToWalletId())) {
            throw new InvalidTransactionException("Cannot transfer to the same wallet");
        }

        // Check for idempotency - return existing transfer if key exists
        // We use a composite key for transfers: "transfer:{idempotencyKey}:out"
        String senderKey = "transfer:" + request.getIdempotencyKey() + ":out";
        String receiverKey = "transfer:" + request.getIdempotencyKey() + ":in";

        Optional<Transaction> existingSenderTx = 
                transactionRepository.findByIdempotencyKey(senderKey);
        
        if (existingSenderTx.isPresent()) {
            log.info("Duplicate transfer detected for key: {}", request.getIdempotencyKey());
            
            Transaction senderTx = existingSenderTx.get();
            Transaction receiverTx = transactionRepository
                    .findByIdempotencyKey(receiverKey)
                    .orElseThrow(() -> new InvalidTransactionException(
                            "Transfer data inconsistency detected"));

            return buildTransferResponse(senderTx, receiverTx, request, true);
        }

        // Lock wallets in consistent order to prevent deadlocks
        // Always lock the wallet with smaller UUID first
        UUID firstId;
        UUID secondId;
        if (request.getFromWalletId().compareTo(request.getToWalletId()) < 0) {
            firstId = request.getFromWalletId();
            secondId = request.getToWalletId();
        } else {
            firstId = request.getToWalletId();
            secondId = request.getFromWalletId();
        }

        Wallet firstWallet = walletRepository.findByIdWithLock(firstId)
                .orElseThrow(() -> new WalletNotFoundException(firstId));
        Wallet secondWallet = walletRepository.findByIdWithLock(secondId)
                .orElseThrow(() -> new WalletNotFoundException(secondId));

        // Determine which is sender and receiver
        Wallet senderWallet = request.getFromWalletId().equals(firstId) 
                ? firstWallet : secondWallet;
        Wallet receiverWallet = request.getToWalletId().equals(firstId) 
                ? firstWallet : secondWallet;

        // Check sufficient balance
        if (senderWallet.getBalance() < request.getAmount()) {
            throw new InsufficientBalanceException(
                    senderWallet.getBalance(), request.getAmount());
        }

        // Generate transfer ID to link both transactions
        UUID transferId = UUID.randomUUID();

        // Debit sender
        Long senderNewBalance = senderWallet.getBalance() - request.getAmount();
        senderWallet.setBalance(senderNewBalance);
        walletRepository.save(senderWallet);

        Transaction senderTransaction = Transaction.builder()
                .idempotencyKey(senderKey)
                .wallet(senderWallet)
                .type(TransactionType.TRANSFER_OUT)
                .amount(request.getAmount())
                .balanceAfter(senderNewBalance)
                .description(request.getDescription())
                .relatedWalletId(receiverWallet.getId())
                .transferId(transferId)
                .build();

        // Credit receiver
        Long receiverNewBalance = receiverWallet.getBalance() + request.getAmount();
        receiverWallet.setBalance(receiverNewBalance);
        walletRepository.save(receiverWallet);

        Transaction receiverTransaction = Transaction.builder()
                .idempotencyKey(receiverKey)
                .wallet(receiverWallet)
                .type(TransactionType.TRANSFER_IN)
                .amount(request.getAmount())
                .balanceAfter(receiverNewBalance)
                .description(request.getDescription())
                .relatedWalletId(senderWallet.getId())
                .transferId(transferId)
                .build();

        // Save both transactions
        Transaction savedSenderTx = transactionRepository.save(senderTransaction);
        Transaction savedReceiverTx = transactionRepository.save(receiverTransaction);

        log.info("Transfer completed - ID: {}, Sender Balance: {}, Receiver Balance: {}",
                transferId, senderNewBalance, receiverNewBalance);

        return buildTransferResponse(savedSenderTx, savedReceiverTx, request, false);
    }

    /**
     * Build transfer response from transaction entities.
     */
    private TransferResponse buildTransferResponse(
            Transaction senderTx, 
            Transaction receiverTx,
            TransferRequest request,
            boolean isDuplicate) {
        
        return TransferResponse.builder()
                .transferId(senderTx.getTransferId())
                .senderTransaction(TransactionResponse.fromEntity(senderTx, isDuplicate))
                .receiverTransaction(TransactionResponse.fromEntity(receiverTx, isDuplicate))
                .amount(request.getAmount())
                .formattedAmount(formatAmount(request.getAmount()))
                .idempotencyKey(request.getIdempotencyKey())
                .createdAt(senderTx.getCreatedAt())
                .duplicate(isDuplicate)
                .build();
    }

    /**
     * Format amount from minor units to display format.
     */
    private String formatAmount(Long amount) {
        if (amount == null) return "0.00";
        long wholePart = amount / 100;
        long decimalPart = Math.abs(amount % 100);
        return String.format("%d.%02d", wholePart, decimalPart);
    }
}

