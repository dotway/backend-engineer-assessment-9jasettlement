package com.wallet.repository;

import com.wallet.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
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


}

