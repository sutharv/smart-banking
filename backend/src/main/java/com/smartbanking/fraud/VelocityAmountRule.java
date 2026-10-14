package com.smartbanking.fraud;

import com.smartbanking.entity.Severity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/** Rule 3: the total amount sent from one account within a window exceeds a per-currency limit. */
@Component
public class VelocityAmountRule implements FraudRule {

    public static final String CODE = "HIGH_VELOCITY_AMOUNT";

    private final FraudProperties.Velocity settings;

    public VelocityAmountRule(FraudProperties properties) {
        this.settings = properties.velocity();
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Optional<RuleHit> evaluate(TransferSnapshot transfer, TransferHistory history) {
        BigDecimal limit = settings.limits().get(transfer.currency());
        if (!settings.enabled() || limit == null) {
            return Optional.empty();
        }
        Instant since = transfer.createdAt().minus(settings.window());
        BigDecimal previous = history.sumCompletedOutgoingSince(transfer.sourceAccountId(), since, transfer.transferId());
        BigDecimal total = previous.add(transfer.amount());
        if (total.compareTo(limit) <= 0) {
            return Optional.empty();
        }
        String explanation = "%s was sent from %s within %s, above the limit of %s."
                .formatted(Formatting.money(total, transfer.currency()), transfer.sourceAccountReference(),
                        Formatting.duration(settings.window()), Formatting.money(limit, transfer.currency()));
        return Optional.of(new RuleHit(CODE, Severity.HIGH, explanation));
    }
}
