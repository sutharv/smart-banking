package com.smartbanking.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransferApiIntegrationTest extends IntegrationTestBase {

    @Test
    void completedTransferMovesMoneyAndPostsBalancedLedgerEntries() throws Exception {
        JsonNode body = read(transfer(bearer("alice"), "SB-EUR-0001", "SB-EUR-0004", "250.00")
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").isNotEmpty())
                .andExpect(jsonPath("$.alerts.length()").value(0)));

        assertThat(balance("SB-EUR-0001")).isEqualByComparingTo("4750.00");
        assertThat(balance("SB-EUR-0004")).isEqualByComparingTo("1750.00");

        String id = body.get("id").asText();
        BigDecimal ledgerSum = jdbc.queryForObject(
                "SELECT SUM(amount) FROM ledger_entry WHERE transfer_id = ?::uuid", BigDecimal.class, id);
        assertThat(ledgerSum).isEqualByComparingTo("0");
        assertThat(count("SELECT COUNT(*) FROM ledger_entry WHERE transfer_id = ?::uuid", id)).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM audit_event WHERE event_type = 'TRANSFER_COMPLETED'")).isEqualTo(1);
    }

    @Test
    void insufficientFundsIsRejectedRecordedAndChangesNothing() throws Exception {
        transfer(bearer("alice"), "SB-EUR-0001", "SB-EUR-0004", "5000.01")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"))
                .andExpect(jsonPath("$.transferId").isNotEmpty());

        assertThat(balance("SB-EUR-0001")).isEqualByComparingTo("5000.00");
        assertThat(balance("SB-EUR-0004")).isEqualByComparingTo("1500.00");
        assertThat(count("SELECT COUNT(*) FROM transfer WHERE status = 'REJECTED' AND rejection_reason = 'INSUFFICIENT_FUNDS'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM ledger_entry WHERE transfer_id IS NOT NULL")).isZero();
    }

    @Test
    void exactBalanceCanBeTransferred() throws Exception {
        transfer(bearer("alice"), "SB-EUR-0001", "SB-EUR-0004", "5000.00").andExpect(status().isCreated());
        assertThat(balance("SB-EUR-0001")).isEqualByComparingTo("0.00");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5.00", "10.001", "1000000.01"})
    void invalidAmountsFailValidationAndAreNotRecorded(String amount) throws Exception {
        transfer(bearer("alice"), "SB-EUR-0001", "SB-EUR-0004", amount)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("amount"));
        assertThat(count("SELECT COUNT(*) FROM transfer")).isZero();
    }

    @Test
    void missingFieldsAndMalformedJsonAreBadRequests() throws Exception {
        String alice = bearer("alice");
        mvc.perform(post("/api/transfers").header("Authorization", alice)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(post("/api/transfers").header("Authorization", alice)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        mvc.perform(post("/api/transfers").header("Authorization", alice).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceAccountReference\":\"SB-EUR-0001\",\"destinationAccountReference\":\"SB-EUR-0004\","
                                + "\"amount\":10,\"currency\":\"GBP\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void sourceAndDestinationMustDiffer() throws Exception {
        transfer(bearer("alice"), "SB-EUR-0001", "SB-EUR-0001", "10.00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SAME_ACCOUNT"));
    }

    @Test
    void currencyMismatchIsRejected() throws Exception {
        transfer(bearer("alice"), "SB-EUR-0001", "SB-PLN-0003", "10.00")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CURRENCY_MISMATCH"));
        transfer(bearer("alice"), "SB-EUR-0001", "SB-EUR-0004", "10.00", "PLN", null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CURRENCY_MISMATCH"));
    }

    @Test
    void inactiveAccountsCannotSendOrReceive() throws Exception {
        transfer(bearer("alice"), "SB-EUR-0001", "SB-EUR-0005", "10.00")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DESTINATION_ACCOUNT_INACTIVE"));
        transfer(bearer("bob"), "SB-EUR-0005", "SB-EUR-0004", "10.00")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SOURCE_ACCOUNT_INACTIVE"));
        assertThat(balance("SB-EUR-0005")).isEqualByComparingTo("200.00");
    }

    @Test
    void customerCannotSpendFromSomeoneElsesAccount() throws Exception {
        transfer(bearer("alice"), "SB-EUR-0004", "SB-EUR-0001", "100.00")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SOURCE_ACCOUNT_NOT_FOUND"));
        assertThat(balance("SB-EUR-0004")).isEqualByComparingTo("1500.00");
        assertThat(count("SELECT COUNT(*) FROM transfer")).isZero();
    }

    @Test
    void unknownDestinationIsNotFound() throws Exception {
        transfer(bearer("alice"), "SB-EUR-0001", "SB-EUR-9999", "10.00")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DESTINATION_ACCOUNT_NOT_FOUND"));
    }

    @Test
    void analystsCannotMoveMoney() throws Exception {
        transfer(bearer("analyst"), "SB-EUR-0001", "SB-EUR-0004", "10.00")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void retryWithSameIdempotencyKeyReturnsOriginalTransferWithoutChargingTwice() throws Exception {
        String alice = bearer("alice");
        String firstId = read(transfer(alice, "SB-EUR-0001", "SB-EUR-0004", "100.00", "EUR", "retry-key-0001")
                .andExpect(status().isCreated())).get("id").asText();

        transfer(alice, "SB-EUR-0001", "SB-EUR-0004", "100.0", "EUR", "retry-key-0001")
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(firstId));

        assertThat(balance("SB-EUR-0001")).isEqualByComparingTo("4900.00");
        assertThat(count("SELECT COUNT(*) FROM transfer")).isEqualTo(1);
    }

    @Test
    void reusingAnIdempotencyKeyForADifferentRequestConflicts() throws Exception {
        String alice = bearer("alice");
        transfer(alice, "SB-EUR-0001", "SB-EUR-0004", "100.00", "EUR", "retry-key-0002").andExpect(status().isCreated());
        transfer(alice, "SB-EUR-0001", "SB-EUR-0004", "200.00", "EUR", "retry-key-0002")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        transfer(alice, "SB-EUR-0001", "SB-EUR-0004", "1.00", "EUR", "bad key!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IDEMPOTENCY_KEY"));
    }

    @Test
    void rejectedOutcomeIsAlsoReplayedForTheSameKey() throws Exception {
        String alice = bearer("alice");
        transfer(alice, "SB-EUR-0001", "SB-EUR-0004", "9000.00", "EUR", "retry-key-0003")
                .andExpect(status().isUnprocessableEntity());
        transfer(alice, "SB-EUR-0001", "SB-EUR-0004", "9000.00", "EUR", "retry-key-0003")
                .andExpect(status().isUnprocessableEntity());
        assertThat(count("SELECT COUNT(*) FROM transfer")).isEqualTo(1);
    }

    @Test
    void highValueTransferCompletesButIsFlaggedWithAnExplanation() throws Exception {
        transfer(bearer("alice"), "SB-EUR-0002", "SB-EUR-0001", "2500.00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.alerts.length()").value(1))
                .andExpect(jsonPath("$.alerts[0].ruleCode").value("HIGH_VALUE_TRANSFER"))
                .andExpect(jsonPath("$.alerts[0].severity").value("MEDIUM"))
                .andExpect(jsonPath("$.alerts[0].explanation")
                        .value("Transfer amount 2,500.00 EUR is above the high-value threshold of 2,000.00 EUR."));
        assertThat(balance("SB-EUR-0002")).isEqualByComparingTo("9500.00");
    }

    @Test
    void transferDetailsAreVisibleToParticipantsOnly() throws Exception {
        String id = read(transfer(bearer("alice"), "SB-EUR-0001", "SB-EUR-0002", "10.00")).get("id").asText();
        mvc.perform(get("/api/transfers/" + id).header("Authorization", bearer("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceAccountReference").value("SB-EUR-0001"));
        mvc.perform(get("/api/transfers/" + id).header("Authorization", bearer("bob")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/transfers/" + id).header("Authorization", bearer("analyst")))
                .andExpect(status().isOk());

        String toBob = read(transfer(bearer("alice"), "SB-EUR-0001", "SB-EUR-0004", "10.00")).get("id").asText();
        mvc.perform(get("/api/transfers/" + toBob).header("Authorization", bearer("bob")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/transfers/not-a-uuid").header("Authorization", bearer("bob")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reconciliationIsBalancedAfterTransfersAndAnalystOnly() throws Exception {
        String alice = bearer("alice");
        transfer(alice, "SB-EUR-0001", "SB-EUR-0004", "123.45").andExpect(status().isCreated());
        transfer(alice, "SB-EUR-0002", "SB-EUR-0001", "2100.00").andExpect(status().isCreated());
        transfer(alice, "SB-EUR-0001", "SB-EUR-0004", "99999.00").andExpect(status().isUnprocessableEntity());

        mvc.perform(get("/api/reconciliation").header("Authorization", bearer("analyst")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanced").value(true))
                .andExpect(jsonPath("$.unbalancedTransfers").value(0));
        mvc.perform(get("/api/reconciliation").header("Authorization", alice))
                .andExpect(status().isForbidden());
    }

    @Test
    void ledgerEntriesCannotBeRewritten() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> jdbc.update("UPDATE ledger_entry SET amount = 1 WHERE account_id = 1"))
                .hasMessageContaining("immutable");
    }
}
