package com.smartbanking.dto;

import com.smartbanking.entity.Currency;
import com.smartbanking.entity.ReviewStatus;
import com.smartbanking.entity.Severity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FraudAlertDto(UUID id, UUID transferId, String sourceAccountReference,
                            String destinationAccountReference, BigDecimal amount, Currency currency,
                            String ruleCode, Severity severity, String explanation,
                            ReviewStatus reviewStatus, String reviewNote, Instant reviewedAt,
                            Instant createdAt) {
}
