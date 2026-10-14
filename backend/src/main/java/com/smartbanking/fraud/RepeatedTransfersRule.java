package com.smartbanking.fraud;

import com.smartbanking.entity.Severity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/** Rule 2: many completed transfers from the same account within a short window. */
@Component
public class RepeatedTransfersRule implements FraudRule {

    public static final String CODE = "RAPID_REPEATED_TRANSFERS";

    private final FraudProperties.RepeatedTransfers settings;

    public RepeatedTransfersRule(FraudProperties properties) {
        this.settings = properties.repeatedTransfers();
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Optional<RuleHit> evaluate(TransferSnapshot transfer, TransferHistory history) {
        if (!settings.enabled() || settings.maxTransfers() <= 0) {
            return Optional.empty();
        }
        // The window is [createdAt - window, createdAt], inclusive of both ends.
        Instant since = transfer.createdAt().minus(settings.window());
        long previous = history.countCompletedOutgoingSince(transfer.sourceAccountId(), since, transfer.transferId());
        long total = previous + 1;
        if (total < settings.maxTransfers()) {
            return Optional.empty();
        }
        String explanation = "%d completed transfers were sent from %s within %s (limit: %d)."
                .formatted(total, transfer.sourceAccountReference(), Formatting.duration(settings.window()),
                        settings.maxTransfers());
        return Optional.of(new RuleHit(CODE, Severity.MEDIUM, explanation));
    }
}
