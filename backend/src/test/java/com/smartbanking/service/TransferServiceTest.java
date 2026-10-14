package com.smartbanking.service;

import com.smartbanking.dto.TransferRequest;
import com.smartbanking.entity.Account;
import com.smartbanking.entity.AccountStatus;
import com.smartbanking.entity.AppUser;
import com.smartbanking.entity.Currency;
import com.smartbanking.entity.LedgerEntry;
import com.smartbanking.entity.RejectionReason;
import com.smartbanking.entity.Role;
import com.smartbanking.entity.Transfer;
import com.smartbanking.entity.TransferStatus;
import com.smartbanking.exception.ApiException;
import com.smartbanking.fraud.FraudMonitoringService;
import com.smartbanking.repository.AccountKey;
import com.smartbanking.repository.AccountRepository;
import com.smartbanking.repository.FraudAlertRepository;
import com.smartbanking.repository.LedgerEntryRepository;
import com.smartbanking.repository.TransferRepository;
import com.smartbanking.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransferServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
    private static final CurrentUser ALICE = new CurrentUser(1L, "alice", Role.CUSTOMER);

    @Mock AccountRepository accountRepository;
    @Mock TransferRepository transferRepository;
    @Mock LedgerEntryRepository ledgerEntryRepository;
    @Mock FraudAlertRepository fraudAlertRepository;
    @Mock FraudMonitoringService fraudMonitoringService;
    @Mock AuditService auditService;

    private TransferService service;
    private Account source;
    private Account destination;

    @BeforeEach
    void setUp() {
        service = new TransferService(accountRepository, transferRepository, ledgerEntryRepository,
                fraudAlertRepository, fraudMonitoringService, auditService, Clock.fixed(NOW, ZoneOffset.UTC));
        AppUser alice = new AppUser("alice", "hash", Role.CUSTOMER, NOW);
        AppUser bob = new AppUser("bob", "hash", Role.CUSTOMER, NOW);
        source = new Account(alice, "SB-EUR-0001", "Alice", Currency.EUR, new BigDecimal("100.00"), AccountStatus.ACTIVE, NOW);
        destination = new Account(bob, "SB-EUR-0004", "Bob", Currency.EUR, new BigDecimal("50.00"), AccountStatus.ACTIVE, NOW);

        when(accountRepository.findKeyByReference("SB-EUR-0001")).thenReturn(Optional.of(new AccountKey(1L, 1L)));
        when(accountRepository.findKeyByReference("SB-EUR-0004")).thenReturn(Optional.of(new AccountKey(4L, 2L)));
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(source));
        when(accountRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(destination));
        when(transferRepository.saveAndFlush(any(Transfer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(fraudMonitoringService.evaluate(any())).thenReturn(List.of());
    }

    private static TransferRequest request(String from, String to, String amount) {
        return new TransferRequest(from, to, new BigDecimal(amount), Currency.EUR, "rent");
    }

    @Test
    void successfulTransferDebitsCreditsAndPostsBalancedLedger() {
        TransferResult result = service.createTransfer(ALICE, request("SB-EUR-0001", "SB-EUR-0004", "40.00"), null);

        assertThat(result.transfer().status()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(source.getBalance()).isEqualByComparingTo("60.00");
        assertThat(destination.getBalance()).isEqualByComparingTo("90.00");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LedgerEntry>> ledger = ArgumentCaptor.forClass(List.class);
        verify(ledgerEntryRepository).saveAll(ledger.capture());
        assertThat(ledger.getValue()).extracting(entry -> entry.getAmount().toPlainString())
                .containsExactly("-40.00", "40.00");
        verify(fraudMonitoringService).evaluate(any(Transfer.class));
    }

    @Test
    void accountsAreAlwaysLockedInAscendingIdOrder() {
        // Bob (id 4) sends to Alice (id 1): id 1 must still be locked first.
        CurrentUser bob = new CurrentUser(2L, "bob", Role.CUSTOMER);
        service.createTransfer(bob, request("SB-EUR-0004", "SB-EUR-0001", "10.00"), null);

        InOrder order = inOrder(accountRepository);
        order.verify(accountRepository).findByIdForUpdate(1L);
        order.verify(accountRepository).findByIdForUpdate(4L);
    }

    @Test
    void insufficientFundsIsRecordedAsRejectedWithoutMovingMoney() {
        TransferResult result = service.createTransfer(ALICE, request("SB-EUR-0001", "SB-EUR-0004", "100.01"), null);

        assertThat(result.transfer().status()).isEqualTo(TransferStatus.REJECTED);
        assertThat(result.transfer().rejectionReason()).isEqualTo(RejectionReason.INSUFFICIENT_FUNDS);
        assertThat(source.getBalance()).isEqualByComparingTo("100.00");
        assertThat(destination.getBalance()).isEqualByComparingTo("50.00");
        verify(ledgerEntryRepository, never()).saveAll(any());
        verify(fraudMonitoringService, never()).evaluate(any());
    }

    @Test
    void sourceOwnedBySomeoneElseIsNotFoundAndNothingIsLocked() {
        CurrentUser mallory = new CurrentUser(9L, "mallory", Role.CUSTOMER);

        assertThatThrownBy(() -> service.createTransfer(mallory, request("SB-EUR-0001", "SB-EUR-0004", "1.00"), null))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getCode()).isEqualTo("SOURCE_ACCOUNT_NOT_FOUND");
                });
        verify(accountRepository, never()).findByIdForUpdate(anyLong());
    }

    @Test
    void sameSourceAndDestinationIsABadRequest() {
        assertThatThrownBy(() -> service.createTransfer(ALICE, request("SB-EUR-0001", "SB-EUR-0001", "1.00"), null))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("SAME_ACCOUNT"));
        verify(accountRepository, never()).findKeyByReference(anyString());
    }

    @Test
    void repeatedIdempotencyKeyWithSameRequestReplaysTheOriginal() {
        TransferRequest request = request("SB-EUR-0001", "SB-EUR-0004", "40.00");
        Transfer original = new Transfer(source, destination, new BigDecimal("40.00"), Currency.EUR, "rent",
                "key-12345678", TransferService.fingerprint(request), 1L, NOW);
        original.complete(NOW);
        when(transferRepository.findByInitiatedByUserIdAndIdempotencyKey(1L, "key-12345678")).thenReturn(Optional.of(original));

        TransferResult result = service.createTransfer(ALICE, request, "key-12345678");

        assertThat(result.replayed()).isTrue();
        assertThat(source.getBalance()).isEqualByComparingTo("100.00");
        verify(accountRepository, never()).findByIdForUpdate(anyLong());
    }

    @Test
    void repeatedIdempotencyKeyWithDifferentRequestConflicts() {
        Transfer original = new Transfer(source, destination, new BigDecimal("40.00"), Currency.EUR, "rent",
                "key-12345678", "different-fingerprint", 1L, NOW);
        when(transferRepository.findByInitiatedByUserIdAndIdempotencyKey(1L, "key-12345678")).thenReturn(Optional.of(original));

        assertThatThrownBy(() -> service.createTransfer(ALICE, request("SB-EUR-0001", "SB-EUR-0004", "40.00"), "key-12345678"))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void fingerprintIgnoresTrailingZerosButNotAmount() {
        String a = TransferService.fingerprint(request("A", "B", "10.0"));
        String b = TransferService.fingerprint(request("A", "B", "10.00"));
        String c = TransferService.fingerprint(request("A", "B", "10.01"));
        assertThat(a).isEqualTo(b).isNotEqualTo(c);
    }
}
