package com.smartbanking.fraud;

import com.smartbanking.entity.Currency;
import com.smartbanking.entity.Severity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

import static com.smartbanking.fraud.RuleTestSupport.properties;
import static com.smartbanking.fraud.RuleTestSupport.transfer;
import static org.assertj.core.api.Assertions.assertThat;

class HighValueTransferRuleTest {

    private final HighValueTransferRule rule = new HighValueTransferRule(properties());
    private final FakeTransferHistory history = new FakeTransferHistory();

    @ParameterizedTest
    @ValueSource(strings = {"0.01", "1999.99", "2000.00"})
    void amountsUpToAndIncludingTheThresholdAreNotFlagged(String amount) {
        assertThat(rule.evaluate(transfer(amount, Currency.EUR), history)).isEmpty();
    }

    @Test
    void amountJustAboveTheThresholdIsFlaggedWithMediumSeverity() {
        RuleHit hit = rule.evaluate(transfer("2000.01", Currency.EUR), history).orElseThrow();
        assertThat(hit.ruleCode()).isEqualTo("HIGH_VALUE_TRANSFER");
        assertThat(hit.severity()).isEqualTo(Severity.MEDIUM);
        assertThat(hit.explanation())
                .isEqualTo("Transfer amount 2,000.01 EUR is above the high-value threshold of 2,000.00 EUR.");
    }

    @Test
    void amountAboveTwiceTheThresholdIsHighSeverity() {
        assertThat(rule.evaluate(transfer("4000.01", Currency.EUR), history).orElseThrow().severity())
                .isEqualTo(Severity.HIGH);
    }

    @Test
    void currencyWithoutAConfiguredThresholdIsNotEvaluated() {
        assertThat(rule.evaluate(transfer("999999.00", Currency.PLN), history)).isEmpty();
    }

    @Test
    void disabledRuleNeverFires() {
        var disabled = new FraudProperties(new FraudProperties.HighValue(false, Map.of(Currency.EUR, BigDecimal.ONE)),
                new FraudProperties.RepeatedTransfers(false, 5, Duration.ofMinutes(2)),
                new FraudProperties.Velocity(false, Duration.ofHours(1), Map.of()));
        assertThat(new HighValueTransferRule(disabled).evaluate(transfer("50000.00", Currency.EUR), history)).isEmpty();
    }
}
