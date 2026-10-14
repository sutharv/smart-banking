package com.smartbanking.fraud;

import com.smartbanking.entity.Currency;
import com.smartbanking.entity.Transfer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** The facts a rule needs about the transfer being evaluated, decoupled from JPA. */
public record TransferSnapshot(UUID transferId, Long sourceAccountId, String sourceAccountReference,
                               BigDecimal amount, Currency currency, Instant createdAt) {

    public static TransferSnapshot of(Transfer transfer) {
        return new TransferSnapshot(transfer.getId(), transfer.getSourceAccount().getId(),
                transfer.getSourceAccount().getReference(), transfer.getAmount(), transfer.getCurrency(),
                transfer.getCreatedAt());
    }
}
