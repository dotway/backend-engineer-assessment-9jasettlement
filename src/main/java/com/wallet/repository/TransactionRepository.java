package com.wallet.repository;

import com.wallet.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Transaction entity operations.
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /**
     * Find transaction by idempotency key.
     * Used to check if a transaction with the same key already exists.
     */
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    /**
     * Check if a transaction with the given idempotency key exists.
     */
    boolean existsByIdempotencyKey(String idempotencyKey);

    /**
     * Find all transactions for a specific wallet, ordered by creation time descending.
     */
    List<Transaction> findByWalletIdOrderByCreatedAtDesc(UUID walletId);
}

