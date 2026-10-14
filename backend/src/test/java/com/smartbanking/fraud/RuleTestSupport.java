package com.smartbanking.fraud;

import com.smartbanking.entity.Currency;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

final class RuleTestSupport {

    static final Long ACCOUNT_ID = 1L;
    static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

    private RuleTestSupport() {
    }

    static TransferSnapshot transfer(String amount, Currency currency) {
        return new TransferSnapshot(UUID.randomUUID(), ACCOUNT_ID, "SB-EUR-0001", new BigDecimal(amount), currency, NOW);
    }

    static FraudProperties properties() {
        return new FraudProperties(
                new FraudProperties.HighValue(true, Map.of(Currency.EUR, new BigDecimal("2000.00"))),
                new FraudProperties.RepeatedTransfers(true, 5, Duration.ofMinutes(2)),
                new FraudProperties.Velocity(true, Duration.ofMinutes(60), Map.of(Currency.EUR, new BigDecimal("5000.00"))));
    }
}
