package com.smartbanking.fraud;

import com.smartbanking.entity.Currency;
import com.smartbanking.entity.Severity;
import org.junit.jupiter.api.Test;

import static com.smartbanking.fraud.RuleTestSupport.ACCOUNT_ID;
import static com.smartbanking.fraud.RuleTestSupport.NOW;
import static com.smartbanking.fraud.RuleTestSupport.properties;
import static com.smartbanking.fraud.RuleTestSupport.transfer;
import static org.assertj.core.api.Assertions.assertThat;

class RepeatedTransfersRuleTest {

    private final RepeatedTransfersRule rule = new RepeatedTransfersRule(properties());

    @Test
    void fourPreviousTransfersInsideTheWindowMakeTheFifthFlagged() {
        FakeTransferHistory history = new FakeTransferHistory();
        for (int i = 1; i <= 4; i++) {
            history.add(ACCOUNT_ID, "10.00", NOW.minusSeconds(20L * i));
        }
        RuleHit hit = rule.evaluate(transfer("10.00", Currency.EUR), history).orElseThrow();
        assertThat(hit.severity()).isEqualTo(Severity.MEDIUM);
        assertThat(hit.explanation()).isEqualTo("5 completed transfers were sent from SB-EUR-0001 within 2 minutes (limit: 5).");
    }

    @Test
    void threePreviousTransfersAreNotEnough() {
        FakeTransferHistory history = new FakeTransferHistory()
                .add(ACCOUNT_ID, "10.00", NOW.minusSeconds(10))
                .add(ACCOUNT_ID, "10.00", NOW.minusSeconds(20))
                .add(ACCOUNT_ID, "10.00", NOW.minusSeconds(30));
        assertThat(rule.evaluate(transfer("10.00", Currency.EUR), history)).isEmpty();
    }

    @Test
    void windowBoundaryIsInclusiveAndOlderTransfersAreIgnored() {
        FakeTransferHistory atBoundary = new FakeTransferHistory();
        FakeTransferHistory justOutside = new FakeTransferHistory();
        for (int i = 0; i < 3; i++) {
            atBoundary.add(ACCOUNT_ID, "1.00", NOW.minusSeconds(5));
            justOutside.add(ACCOUNT_ID, "1.00", NOW.minusSeconds(5));
        }
        atBoundary.add(ACCOUNT_ID, "1.00", NOW.minusSeconds(120));      // exactly 2 minutes earlier: counts
        justOutside.add(ACCOUNT_ID, "1.00", NOW.minusSeconds(121));     // 2 minutes and 1 second: ignored

        assertThat(rule.evaluate(transfer("1.00", Currency.EUR), atBoundary)).isPresent();
        assertThat(rule.evaluate(transfer("1.00", Currency.EUR), justOutside)).isEmpty();
    }

    @Test
    void otherAccountsActivityDoesNotCount() {
        FakeTransferHistory history = new FakeTransferHistory();
        for (int i = 0; i < 10; i++) {
            history.add(99L, "1.00", NOW.minusSeconds(1));
        }
        assertThat(rule.evaluate(transfer("1.00", Currency.EUR), history)).isEmpty();
    }
}
