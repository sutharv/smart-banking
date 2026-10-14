package com.smartbanking.dto;

import com.smartbanking.entity.Currency;
import com.smartbanking.entity.RejectionReason;
import com.smartbanking.entity.TransferStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A transfer as seen from one account's point of view. */
public record TransactionDto(UUID transferId, Direction direction, String counterpartyReference,
                             BigDecimal amount, Currency currency, TransferStatus status,
                             RejectionReason rejectionReason, String reference, Instant createdAt,
                             boolean flagged) {

    public enum Direction {
        INCOMING,
        OUTGOING
    }
}
