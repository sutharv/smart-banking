package com.smartbanking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * A fictional account. The stored balance is a cached projection of the ledger:
 * it must always equal the sum of the account's ledger entries.
 */
@Entity
@Table(name = "account")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false, updatable = false)
    private AppUser owner;

    /** Public, non-sensitive identifier used in URLs and the UI instead of the database id. */
    @Column(name = "account_reference", nullable = false, unique = true, length = 34, updatable = false)
    private String reference;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3, updatable = false)
    private Currency currency;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Account() {
        // for JPA
    }

    public Account(AppUser owner, String reference, String displayName, Currency currency,
                   BigDecimal balance, AccountStatus status, Instant createdAt) {
        this.owner = Objects.requireNonNull(owner);
        this.reference = Objects.requireNonNull(reference);
        this.displayName = Objects.requireNonNull(displayName);
        this.currency = Objects.requireNonNull(currency);
        this.balance = Objects.requireNonNull(balance);
        this.status = Objects.requireNonNull(status);
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    public boolean canCover(BigDecimal amount) {
        return balance.compareTo(amount) >= 0;
    }

    public void debit(BigDecimal amount) {
        requirePositive(amount);
        if (!canCover(amount)) {
            throw new IllegalStateException("Debit would make balance negative for account " + reference);
        }
        balance = balance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        requirePositive(amount);
        balance = balance.add(amount);
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }

    public boolean isOwnedBy(Long userId) {
        return owner.getId().equals(userId);
    }

    public Long getId() {
        return id;
    }

    public AppUser getOwner() {
        return owner;
    }

    public String getReference() {
        return reference;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Currency getCurrency() {
        return currency;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
