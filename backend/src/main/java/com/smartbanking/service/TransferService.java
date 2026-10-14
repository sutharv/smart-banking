package com.smartbanking.service;

import com.smartbanking.dto.TransferDto;
import com.smartbanking.dto.TransferRequest;
import com.smartbanking.entity.Account;
import com.smartbanking.entity.AuditEventType;
import com.smartbanking.entity.FraudAlert;
import com.smartbanking.entity.LedgerEntry;
import com.smartbanking.entity.RejectionReason;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Moves money between accounts. Everything for one transfer - status, ledger entries, balance
 * updates, fraud alerts and the audit record - happens in a single database transaction, so a
 * failure at any step leaves no partial debit or credit.
 *
 * <p>Concurrency: both account rows are locked with {@code SELECT ... FOR UPDATE} in ascending id
 * order before balances are checked. Two transfers touching the same account are therefore
 * serialised (no double spending), and the fixed lock order prevents deadlocks between opposite
 * transfers (A to B while B to A).
 */
@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final FraudAlertRepository fraudAlertRepository;
    private final FraudMonitoringService fraudMonitoringService;
    private final AuditService auditService;
    private final Clock clock;

    public TransferService(AccountRepository accountRepository, TransferRepository transferRepository,
                           LedgerEntryRepository ledgerEntryRepository, FraudAlertRepository fraudAlertRepository,
                           FraudMonitoringService fraudMonitoringService, AuditService auditService, Clock clock) {
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.fraudAlertRepository = fraudAlertRepository;
        this.fraudMonitoringService = fraudMonitoringService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public TransferResult createTransfer(CurrentUser user, TransferRequest request, String idempotencyKey) {
        if (request.sourceAccountReference().equals(request.destinationAccountReference())) {
            throw ApiException.badRequest("SAME_ACCOUNT", "Source and destination accounts must be different.");
        }

        String fingerprint = null;
        if (idempotencyKey != null) {
            fingerprint = fingerprint(request);
            Optional<Transfer> previous = transferRepository.findByInitiatedByUserIdAndIdempotencyKey(user.id(), idempotencyKey);
            if (previous.isPresent()) {
                return replay(previous.get(), fingerprint);
            }
        }

        // 1. Authorise: the caller must own the source account. Other users' accounts look "not found".
        AccountKey sourceKey = accountRepository.findKeyByReference(request.sourceAccountReference())
                .filter(key -> key.ownerUserId().equals(user.id()))
                .orElseThrow(() -> ApiException.notFound("SOURCE_ACCOUNT_NOT_FOUND", "Source account not found."));
        AccountKey destinationKey = accountRepository.findKeyByReference(request.destinationAccountReference())
                .orElseThrow(() -> ApiException.notFound("DESTINATION_ACCOUNT_NOT_FOUND", "Destination account not found."));

        // 2. Lock both rows in a consistent order, then read their current state.
        Account source;
        Account destination;
        if (sourceKey.id() < destinationKey.id()) {
            source = lock(sourceKey.id());
            destination = lock(destinationKey.id());
        } else {
            destination = lock(destinationKey.id());
            source = lock(sourceKey.id());
        }

        Instant now = clock.instant();
        Transfer transfer = new Transfer(source, destination, request.amount(), request.currency(),
                normaliseReference(request.reference()), idempotencyKey, fingerprint, user.id(), now);

        // 3. Business rules, checked against the locked (current) balances and statuses.
        Optional<RejectionReason> rejection = checkRules(source, destination, request);
        if (rejection.isPresent()) {
            transfer.reject(rejection.get());
            transferRepository.saveAndFlush(transfer);
            auditService.record(user.id(), AuditEventType.TRANSFER_REJECTED, "transfer:" + transfer.getId());
            log.info("Transfer {} rejected: {}", transfer.getId(), rejection.get());
            return new TransferResult(DtoMapper.toDto(transfer, List.of()), false);
        }

        // 4. Post the double-entry ledger and update the cached balances.
        source.debit(transfer.getAmount());
        destination.credit(transfer.getAmount());
        transfer.complete(now);
        transferRepository.saveAndFlush(transfer);
        ledgerEntryRepository.saveAll(List.of(LedgerEntry.debit(transfer, now), LedgerEntry.credit(transfer, now)));

        // 5. Monitoring flags the transfer for review but never blocks it.
        List<FraudAlert> alerts = fraudMonitoringService.evaluate(transfer);
        auditService.record(user.id(), AuditEventType.TRANSFER_COMPLETED, "transfer:" + transfer.getId());
        for (FraudAlert alert : alerts) {
            auditService.record(null, AuditEventType.FRAUD_ALERT_RAISED, "alert:" + alert.getId());
        }
        log.info("Transfer {} completed between accounts {} and {}", transfer.getId(),
                source.getReference(), destination.getReference());
        return new TransferResult(DtoMapper.toDto(transfer, alerts), false);
    }

    @Transactional(readOnly = true)
    public TransferDto getTransfer(CurrentUser user, UUID id) {
        Transfer transfer = transferRepository.findById(id)
                .filter(t -> canView(user, t))
                .orElseThrow(() -> ApiException.notFound("TRANSFER_NOT_FOUND", "Transfer not found."));
        return DtoMapper.toDto(transfer, fraudAlertRepository.findByTransferIdOrderByCreatedAtAsc(transfer.getId()));
    }

    private static boolean canView(CurrentUser user, Transfer transfer) {
        if (user.isAnalyst() || transfer.getSourceAccount().isOwnedBy(user.id())) {
            return true;
        }
        // Recipients see transfers that actually credited them, not rejected attempts.
        return transfer.getStatus() == TransferStatus.COMPLETED && transfer.getDestinationAccount().isOwnedBy(user.id());
    }

    private static Optional<RejectionReason> checkRules(Account source, Account destination, TransferRequest request) {
        if (!source.isActive()) {
            return Optional.of(RejectionReason.SOURCE_ACCOUNT_INACTIVE);
        }
        if (!destination.isActive()) {
            return Optional.of(RejectionReason.DESTINATION_ACCOUNT_INACTIVE);
        }
        if (source.getCurrency() != request.currency() || destination.getCurrency() != request.currency()) {
            return Optional.of(RejectionReason.CURRENCY_MISMATCH);
        }
        if (!source.canCover(request.amount())) {
            return Optional.of(RejectionReason.INSUFFICIENT_FUNDS);
        }
        return Optional.empty();
    }

    private TransferResult replay(Transfer previous, String fingerprint) {
        if (!fingerprint.equals(previous.getRequestFingerprint())) {
            throw ApiException.conflict("IDEMPOTENCY_KEY_REUSED",
                    "This Idempotency-Key was already used for a different transfer request.");
        }
        List<FraudAlert> alerts = fraudAlertRepository.findByTransferIdOrderByCreatedAtAsc(previous.getId());
        return new TransferResult(DtoMapper.toDto(previous, alerts), true);
    }

    private Account lock(Long accountId) {
        return accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new IllegalStateException("Account " + accountId + " disappeared during transfer"));
    }

    private static String normaliseReference(String reference) {
        if (reference == null || reference.isBlank()) {
            return null;
        }
        return reference.strip();
    }

    /** Identifies "the same request" for idempotency: accounts, amount (scale-insensitive), currency and reference. */
    static String fingerprint(TransferRequest request) {
        String canonical = String.join("|",
                request.sourceAccountReference(),
                request.destinationAccountReference(),
                request.amount().stripTrailingZeros().toPlainString(),
                request.currency().name(),
                String.valueOf(normaliseReference(request.reference())));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
