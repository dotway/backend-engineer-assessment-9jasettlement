package com.wallet.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing a wallet in the system.
 * 
 * Balance is stored in minor units (e.g., cents for USD) to avoid
 * floating-point precision issues with monetary calculations.
 */
@Entity
@Table(name = "wallets")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Optional name/label for the wallet
     */
    @Column(nullable = true)
    private String name;

    /**
     * Balance stored in minor units (e.g., cents).
     * 100 = $1.00, 1050 = $10.50
     */
    @Column(nullable = false)
    private Long balance;

    /**
     * Currency code (e.g., "USD", "EUR")
     */
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Version for optimistic locking to prevent concurrent update issues
     */
    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (balance == null) {
            balance = 0L;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

