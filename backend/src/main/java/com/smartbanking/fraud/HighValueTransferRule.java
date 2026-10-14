package com.smartbanking.fraud;

import com.smartbanking.entity.Severity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/** Rule 1: a single transfer above a configurable per-currency threshold. */
@Component
public class HighValueTransferRule implements FraudRule {

    public static final String CODE = "HIGH_VALUE_TRANSFER";

    private final FraudProperties.HighValue settings;

    public HighValueTransferRule(FraudProperties properties) {
        this.settings = properties.highValue();
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Optional<RuleHit> evaluate(TransferSnapshot transfer, TransferHistory history) {
        BigDecimal threshold = settings.thresholds().get(transfer.currency());
        if (!settings.enabled() || threshold == null || transfer.amount().compareTo(threshold) <= 0) {
            return Optional.empty();
        }
        // Amounts more than double the threshold are treated as more urgent for review.
        Severity severity = transfer.amount().compareTo(threshold.multiply(BigDecimal.TWO)) > 0
                ? Severity.HIGH : Severity.MEDIUM;
        String explanation = "Transfer amount %s is above the high-value threshold of %s."
                .formatted(Formatting.money(transfer.amount(), transfer.currency()),
                        Formatting.money(threshold, transfer.currency()));
        return Optional.of(new RuleHit(CODE, severity, explanation));
    }
}
