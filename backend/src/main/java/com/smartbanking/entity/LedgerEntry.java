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
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One side of a double-entry posting. Debits are negative and credits positive, so the
 * entries of a completed transfer sum to zero. Rows are immutable (also enforced by a DB trigger).
 */
@Entity
@Immutable
@Table(name = "ledger_entry")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id")
    private Transfer transfer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 20)
    private EntryType entryType;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LedgerEntry() {
        // for JPA
    }

    private LedgerEntry(Transfer transfer, Account account, BigDecimal amount, EntryType entryType, Instant createdAt) {
        this.transfer = transfer;
        this.account = account;
        this.amount = amount;
        this.entryType = entryType;
        this.createdAt = createdAt;
    }

    public static LedgerEntry debit(Transfer transfer, Instant when) {
        return new LedgerEntry(transfer, transfer.getSourceAccount(), transfer.getAmount().negate(), EntryType.DEBIT, when);
    }

    public static LedgerEntry credit(Transfer transfer, Instant when) {
        return new LedgerEntry(transfer, transfer.getDestinationAccount(), transfer.getAmount(), EntryType.CREDIT, when);
    }

    public Long getId() {
        return id;
    }

    public Transfer getTransfer() {
        return transfer;
    }

    public Account getAccount() {
        return account;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public EntryType getEntryType() {
        return entryType;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
