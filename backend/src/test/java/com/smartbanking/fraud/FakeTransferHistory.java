package com.smartbanking.fraud;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** In-memory history of completed outgoing transfers for rule unit tests. */
class FakeTransferHistory implements TransferHistory {

    record Entry(UUID id, Long accountId, BigDecimal amount, Instant createdAt) {
    }

    private final List<Entry> entries = new ArrayList<>();

    FakeTransferHistory add(Long accountId, String amount, Instant createdAt) {
        entries.add(new Entry(UUID.randomUUID(), accountId, new BigDecimal(amount), createdAt));
        return this;
    }

    @Override
    public long countCompletedOutgoingSince(Long accountId, Instant since, UUID excludeTransferId) {
        return matching(accountId, since, excludeTransferId).count();
    }

    @Override
    public BigDecimal sumCompletedOutgoingSince(Long accountId, Instant since, UUID excludeTransferId) {
        return matching(accountId, since, excludeTransferId).map(Entry::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private java.util.stream.Stream<Entry> matching(Long accountId, Instant since, UUID excludeTransferId) {
        return entries.stream()
                .filter(e -> e.accountId().equals(accountId))
                .filter(e -> !e.createdAt().isBefore(since))
                .filter(e -> !e.id().equals(excludeTransferId));
    }
}
