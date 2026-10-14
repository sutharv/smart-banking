package com.smartbanking.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainModelTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
    private final AppUser owner = new AppUser("alice", "hash", Role.CUSTOMER, NOW);

    private Account account(String balance) {
        return new Account(owner, "SB-EUR-0001", "Test", Currency.EUR, new BigDecimal(balance), AccountStatus.ACTIVE, NOW);
    }

    @Test
    void debitAndCreditUseExactDecimalArithmetic() {
        Account account = account("0.30");
        account.debit(new BigDecimal("0.10"));
        account.debit(new BigDecimal("0.20"));
        assertThat(account.getBalance()).isEqualByComparingTo("0.00");
        account.credit(new BigDecimal("0.10"));
        assertThat(account.getBalance()).isEqualByComparingTo("0.10");
    }

    @Test
    void accountCannotGoNegativeOrMoveNonPositiveAmounts() {
        Account account = account("10.00");
        assertThatThrownBy(() -> account.debit(new BigDecimal("10.01"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> account.credit(BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account.debit(new BigDecimal("-1"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(account.getBalance()).isEqualByComparingTo("10.00");
    }

    @Test
    void transferMovesFromPendingToExactlyOneTerminalState() {
        Transfer completed = newTransfer();
        assertThat(completed.getStatus()).isEqualTo(TransferStatus.PENDING);
        completed.complete(NOW);
        assertThat(completed.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThatThrownBy(() -> completed.reject(RejectionReason.INSUFFICIENT_FUNDS)).isInstanceOf(IllegalStateException.class);

        Transfer rejected = newTransfer();
        rejected.reject(RejectionReason.CURRENCY_MISMATCH);
        assertThat(rejected.getRejectionReason()).isEqualTo(RejectionReason.CURRENCY_MISMATCH);
        assertThatThrownBy(() -> rejected.complete(NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void ledgerEntriesForATransferSumToZero() {
        Transfer transfer = newTransfer();
        LedgerEntry debit = LedgerEntry.debit(transfer, NOW);
        LedgerEntry credit = LedgerEntry.credit(transfer, NOW);
        assertThat(debit.getAmount().add(credit.getAmount())).isEqualByComparingTo("0");
        assertThat(debit.getAmount()).isNegative();
        assertThat(debit.getEntryType()).isEqualTo(EntryType.DEBIT);
    }

    @Test
    void alertCanBeReviewedOnceOnly() {
        FraudAlert alert = new FraudAlert(newTransfer(), "HIGH_VALUE_TRANSFER", "why", Severity.MEDIUM, NOW);
        assertThatThrownBy(() -> alert.review(ReviewStatus.OPEN, null, 3L, NOW)).isInstanceOf(IllegalArgumentException.class);
        alert.review(ReviewStatus.DISMISSED, "known payee", 3L, NOW);
        assertThat(alert.getReviewStatus()).isEqualTo(ReviewStatus.DISMISSED);
        assertThatThrownBy(() -> alert.review(ReviewStatus.CONFIRMED, null, 3L, NOW)).isInstanceOf(IllegalStateException.class);
    }

    private Transfer newTransfer() {
        Account destination = new Account(owner, "SB-EUR-0002", "Other", Currency.EUR, BigDecimal.ZERO, AccountStatus.ACTIVE, NOW);
        return new Transfer(account("100.00"), destination, new BigDecimal("25.00"), Currency.EUR,
                null, null, null, 1L, NOW);
    }
}
