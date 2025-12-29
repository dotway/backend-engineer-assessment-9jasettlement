package com.wallet.entity;

import com.wallet.enums.TransactionType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing a transaction on a wallet.
 * 
 * Transactions can be:
 * - CREDIT: Adding funds to a wallet
 * - DEBIT: Removing funds from a wallet
 * - TRANSFER_IN: Receiving funds from another wallet
 * - TRANSFER_OUT: Sending funds to another wallet
 */
@Entity
@Table(name = "transactions", indexes = {
    @Index(name = "idx_idempotency_key", columnList = "idempotency_key", unique = true),
    @Index(name = "idx_wallet_id", columnList = "wallet_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Unique key to ensure idempotency of transactions.
     * If the same key is used twice, the second request is ignored.
     */
    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    /**
     * The wallet this transaction belongs to
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    /**
     * Type of transaction
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    /**
     * Amount in minor units (always positive).
     * The type determines if it's added or subtracted.
     */
    @Column(nullable = false)
    private Long amount;

    /**
     * Balance after this transaction was applied
     */
    @Column(name = "balance_after", nullable = false)
    private Long balanceAfter;

    /**
     * Optional description for the transaction
     */
    @Column
    private String description;

    /**
     * For transfers: the other wallet involved
     */
    @Column(name = "related_wallet_id")
    private UUID relatedWalletId;

    /**
     * For transfers: links the two transaction records together
     */
    @Column(name = "transfer_id")
    private UUID transferId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
