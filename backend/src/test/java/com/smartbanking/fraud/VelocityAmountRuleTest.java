package com.smartbanking.fraud;

import com.smartbanking.entity.Currency;
import com.smartbanking.entity.Severity;
import org.junit.jupiter.api.Test;

import static com.smartbanking.fraud.RuleTestSupport.ACCOUNT_ID;
import static com.smartbanking.fraud.RuleTestSupport.NOW;
import static com.smartbanking.fraud.RuleTestSupport.properties;
import static com.smartbanking.fraud.RuleTestSupport.transfer;
import static org.assertj.core.api.Assertions.assertThat;

class VelocityAmountRuleTest {

    private final VelocityAmountRule rule = new VelocityAmountRule(properties());

    @Test
    void totalReachingExactlyTheLimitIsNotFlagged() {
        FakeTransferHistory history = new FakeTransferHistory().add(ACCOUNT_ID, "3000.00", NOW.minusSeconds(600));
        assertThat(rule.evaluate(transfer("2000.00", Currency.EUR), history)).isEmpty();
    }

    @Test
    void totalAboveTheLimitIsFlaggedWithTheExactTotalExplained() {
        FakeTransferHistory history = new FakeTransferHistory()
                .add(ACCOUNT_ID, "1900.00", NOW.minusSeconds(1800))
                .add(ACCOUNT_ID, "1900.00", NOW.minusSeconds(900));
        RuleHit hit = rule.evaluate(transfer("1900.00", Currency.EUR), history).orElseThrow();
        assertThat(hit.ruleCode()).isEqualTo("HIGH_VELOCITY_AMOUNT");
        assertThat(hit.severity()).isEqualTo(Severity.HIGH);
        assertThat(hit.explanation())
                .isEqualTo("5,700.00 EUR was sent from SB-EUR-0001 within 1 hour, above the limit of 5,000.00 EUR.");
    }

    @Test
    void transfersOlderThanTheWindowAreIgnored() {
        FakeTransferHistory history = new FakeTransferHistory().add(ACCOUNT_ID, "4900.00", NOW.minusSeconds(3601));
        assertThat(rule.evaluate(transfer("1000.00", Currency.EUR), history)).isEmpty();
    }
}
