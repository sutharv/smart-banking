package com.smartbanking.dto;

import com.smartbanking.entity.Currency;
import com.smartbanking.entity.RejectionReason;
import com.smartbanking.entity.TransferStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TransferDto(UUID id, String sourceAccountReference, String destinationAccountReference,
                          BigDecimal amount, Currency currency, TransferStatus status,
                          RejectionReason rejectionReason, String reference, Instant createdAt,
                          Instant completedAt, List<FraudAlertSummary> alerts) {
}
