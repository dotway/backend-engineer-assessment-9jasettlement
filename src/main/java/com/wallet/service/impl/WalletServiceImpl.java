package com.wallet.service.impl;

import com.wallet.constants.WalletConstants;
import com.wallet.dto.*;
import com.wallet.entity.Transaction;
import com.wallet.entity.Wallet;
import com.wallet.enums.TransactionType;
import com.wallet.exception.InsufficientBalanceException;
import com.wallet.exception.InvalidTransactionException;
import com.wallet.exception.WalletNotFoundException;
import com.wallet.repository.TransactionRepository;
import com.wallet.repository.WalletRepository;
import com.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    @Override
    @Transactional
    public WalletResponse createWallet(CreateWalletRequest request) {
        log.info("Creating wallet with name: {}, currency: {}", request.getName(), request.getCurrency());

        Wallet wallet = Wallet.builder()
                .name(request.getName())
                .balance(request.getInitialBalance() != null 
                        ? request.getInitialBalance() 
                        : WalletConstants.DEFAULT_BALANCE)
                .currency(request.getCurrency() != null 
                        ? request.getCurrency() 
                        : WalletConstants.DEFAULT_CURRENCY)
                .build();

        Wallet savedWallet = walletRepository.save(wallet);
        log.info("Created wallet with ID: {}", savedWallet.getId());

        return WalletResponse.fromEntity(savedWallet);
    }

    @Override
    @Transactional(readOnly = true)
    public WalletResponse getWallet(UUID walletId) {
        log.debug("Getting wallet: {}", walletId);
        
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId));

        return WalletResponse.fromEntity(wallet);
    }

    @Override
    @Transactional
    public TransactionResponse processTransaction(TransactionRequest request) {
        log.info("Processing transaction - Type: {}, Wallet: {}, Amount: {}, Key: {}",
                request.getType(), request.getWalletId(), request.getAmount(), request.getIdempotencyKey());

        // Check for idempotency - return existing transaction if the key exists
        Optional<Transaction> existingTransaction = 
                transactionRepository.findByIdempotencyKey(request.getIdempotencyKey());
        
        if (existingTransaction.isPresent()) {
            log.info("Duplicate transaction detected for key: {}", request.getIdempotencyKey());
            return TransactionResponse.fromEntity(existingTransaction.get(), true);
        }

        // Validate transaction type
        TransactionType type = parseTransactionType(request.getType());

        if (type != TransactionType.CREDIT && type != TransactionType.DEBIT) {
            throw new InvalidTransactionException(
                    "Transaction type must be CREDIT or DEBIT for this endpoint");
        }

        // Get wallet with lock to prevent concurrent modifications
        Wallet wallet = walletRepository.findByIdWithLock(request.getWalletId())
                .orElseThrow(() -> new WalletNotFoundException(request.getWalletId()));

        // Calculate new balance
        Long newBalance = calculateNewBalance(wallet, type, request.getAmount());

        // Update wallet balance
        wallet.setBalance(newBalance);
        walletRepository.save(wallet);

        // Create a transaction record
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

    @Override
    @Transactional
    public TransferResponse processTransfer(TransferRequest request) {
        log.info("Processing transfer - From: {}, To: {}, Amount: {}, Key: {}",
                request.getFromWalletId(), request.getToWalletId(), 
                request.getAmount(), request.getIdempotencyKey());

        // Validate: cannot transfer to the same wallet
        if (request.getFromWalletId().equals(request.getToWalletId())) {
            throw new InvalidTransactionException("Cannot transfer to the same wallet");
        }

        // Check for idempotency - return existing transfer if key exists
        String senderKey = buildTransferSenderKey(request.getIdempotencyKey());
        String receiverKey = buildTransferReceiverKey(request.getIdempotencyKey());

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
        Wallet[] wallets = lockWalletsInOrder(request.getFromWalletId(), request.getToWalletId());
        Wallet senderWallet = wallets[0];
        Wallet receiverWallet = wallets[1];

        // Check sufficient balance
        if (senderWallet.getBalance() < request.getAmount()) {
            throw new InsufficientBalanceException(
                    senderWallet.getBalance(), request.getAmount());
        }

        // Generate transfer ID to link both transactions
        UUID transferId = UUID.randomUUID();

        // Process sender debit
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

        // Process receiver credit
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
     * Parse and validate transaction type from string.
     */
    private TransactionType parseTransactionType(String type) {
        try {
            return TransactionType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidTransactionException(
                    "Invalid transaction type. Must be CREDIT or DEBIT");
        }
    }

    /**
     * Calculate a new balance based on a transaction type.
     */
    private Long calculateNewBalance(Wallet wallet, TransactionType type, Long amount) {
        if (type == TransactionType.CREDIT) {
            return wallet.getBalance() + amount;
        } else {
            if (wallet.getBalance() < amount) {
                throw new InsufficientBalanceException(wallet.getBalance(), amount);
            }
            return wallet.getBalance() - amount;
        }
    }

    /**
     * Lock wallets in consistent order to prevent deadlocks.
     * Returns array where [0] is sender, [1] is receiver.
     */
    private Wallet[] lockWalletsInOrder(UUID fromWalletId, UUID toWalletId) {
        UUID firstId;
        UUID secondId;
        
        if (fromWalletId.compareTo(toWalletId) < 0) {
            firstId = fromWalletId;
            secondId = toWalletId;
        } else {
            firstId = toWalletId;
            secondId = fromWalletId;
        }

        Wallet firstWallet = walletRepository.findByIdWithLock(firstId)
                .orElseThrow(() -> new WalletNotFoundException(firstId));
        Wallet secondWallet = walletRepository.findByIdWithLock(secondId)
                .orElseThrow(() -> new WalletNotFoundException(secondId));

        Wallet senderWallet = fromWalletId.equals(firstId) ? firstWallet : secondWallet;
        Wallet receiverWallet = toWalletId.equals(firstId) ? firstWallet : secondWallet;

        return new Wallet[]{senderWallet, receiverWallet};
    }

    /**
     * Build transfer sender idempotency key.
     */
    private String buildTransferSenderKey(String idempotencyKey) {
        return WalletConstants.TRANSFER_SENDER_KEY_PREFIX 
                + idempotencyKey 
                + WalletConstants.TRANSFER_SENDER_KEY_SUFFIX;
    }

    /**
     * Build transfer receiver idempotency key.
     */
    private String buildTransferReceiverKey(String idempotencyKey) {
        return WalletConstants.TRANSFER_SENDER_KEY_PREFIX 
                + idempotencyKey 
                + WalletConstants.TRANSFER_RECEIVER_KEY_SUFFIX;
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
        long wholePart = amount / WalletConstants.MINOR_UNITS_DIVISOR;
        long decimalPart = Math.abs(amount % WalletConstants.MINOR_UNITS_DIVISOR);
        return String.format("%d.%02d", wholePart, decimalPart);
    }
}

