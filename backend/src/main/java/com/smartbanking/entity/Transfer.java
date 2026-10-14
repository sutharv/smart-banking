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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A request to move money between two accounts and its outcome.
 * Status transitions are enforced here: PENDING -> COMPLETED or PENDING -> REJECTED.
 */
@Entity
@Table(name = "transfer")
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_account_id", nullable = false, updatable = false)
    private Account sourceAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_account_id", nullable = false, updatable = false)
    private Account destinationAccount;

    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3, updatable = false)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransferStatus status = TransferStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "rejection_reason", length = 40)
    private RejectionReason rejectionReason;

    /** Free-text payment reference supplied by the customer. */
    @Column(length = 140, updatable = false)
    private String reference;

    @Column(name = "idempotency_key", length = 80, updatable = false)
    private String idempotencyKey;

    /** SHA-256 of the request body, used to detect an idempotency key reused for a different request. */
    @Column(name = "request_fingerprint", length = 64, updatable = false)
    private String requestFingerprint;

    @Column(name = "initiated_by_user_id", nullable = false, updatable = false)
    private Long initiatedByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected Transfer() {
        // for JPA
    }

    public Transfer(Account sourceAccount, Account destinationAccount, BigDecimal amount, Currency currency,
                    String reference, String idempotencyKey, String requestFingerprint,
                    Long initiatedByUserId, Instant createdAt) {
        this.sourceAccount = Objects.requireNonNull(sourceAccount);
        this.destinationAccount = Objects.requireNonNull(destinationAccount);
        this.amount = Objects.requireNonNull(amount);
        this.currency = Objects.requireNonNull(currency);
        this.reference = reference;
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
        this.initiatedByUserId = Objects.requireNonNull(initiatedByUserId);
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public void complete(Instant when) {
        requirePending();
        this.status = TransferStatus.COMPLETED;
        this.completedAt = Objects.requireNonNull(when);
    }

    public void reject(RejectionReason reason) {
        requirePending();
        this.status = TransferStatus.REJECTED;
        this.rejectionReason = Objects.requireNonNull(reason);
    }

    private void requirePending() {
        if (status != TransferStatus.PENDING) {
            throw new IllegalStateException("Transfer " + id + " is already " + status);
        }
    }

    public UUID getId() {
        return id;
    }

    public Account getSourceAccount() {
        return sourceAccount;
    }

    public Account getDestinationAccount() {
        return destinationAccount;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Currency getCurrency() {
        return currency;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public RejectionReason getRejectionReason() {
        return rejectionReason;
    }

    public String getReference() {
        return reference;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public Long getInitiatedByUserId() {
        return initiatedByUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
