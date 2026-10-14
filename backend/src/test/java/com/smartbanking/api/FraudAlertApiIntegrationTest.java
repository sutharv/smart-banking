package com.smartbanking.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FraudAlertApiIntegrationTest extends IntegrationTestBase {

    @Test
    void fifthTransferWithinTheWindowTriggersTheRepeatedTransfersRule() throws Exception {
        String alice = bearer("alice");
        for (int i = 1; i <= 4; i++) {
            transfer(alice, "SB-EUR-0001", "SB-EUR-0004", "10.00")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.alerts.length()").value(0));
        }
        transfer(alice, "SB-EUR-0001", "SB-EUR-0004", "10.00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.alerts[0].ruleCode").value("RAPID_REPEATED_TRANSFERS"))
                .andExpect(jsonPath("$.alerts[0].explanation")
                        .value("5 completed transfers were sent from SB-EUR-0001 within 2 minutes (limit: 5)."));
    }

    @Test
    void rejectedAttemptsDoNotCountTowardsMonitoringRules() throws Exception {
        String alice = bearer("alice");
        for (int i = 0; i < 4; i++) {
            transfer(alice, "SB-EUR-0001", "SB-EUR-0004", "99999.00").andExpect(status().isUnprocessableEntity());
        }
        transfer(alice, "SB-EUR-0001", "SB-EUR-0004", "10.00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.alerts.length()").value(0));
    }

    @Test
    void cumulativeAmountAboveTheLimitTriggersTheVelocityRule() throws Exception {
        String alice = bearer("alice");
        transfer(alice, "SB-EUR-0002", "SB-EUR-0001", "1900.00").andExpect(jsonPath("$.alerts.length()").value(0));
        transfer(alice, "SB-EUR-0002", "SB-EUR-0001", "1900.00").andExpect(jsonPath("$.alerts.length()").value(0));
        transfer(alice, "SB-EUR-0002", "SB-EUR-0001", "1900.00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.alerts.length()").value(1))
                .andExpect(jsonPath("$.alerts[0].ruleCode").value("HIGH_VELOCITY_AMOUNT"))
                .andExpect(jsonPath("$.alerts[0].severity").value("HIGH"));
    }

    @Test
    void analystSeesAndFiltersAllAlertsWhileCustomersSeeOnlyTheirOwn() throws Exception {
        transfer(bearer("alice"), "SB-EUR-0002", "SB-EUR-0001", "2500.00").andExpect(status().isCreated());
        transfer(bearer("bob"), "SB-EUR-0004", "SB-EUR-0001", "10.00").andExpect(status().isCreated());

        String analyst = bearer("analyst");
        mvc.perform(get("/api/fraud-alerts").header("Authorization", analyst))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].sourceAccountReference").value("SB-EUR-0002"))
                .andExpect(jsonPath("$.content[0].reviewStatus").value("OPEN"));
        mvc.perform(get("/api/fraud-alerts?ruleCode=RAPID_REPEATED_TRANSFERS").header("Authorization", analyst))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/fraud-alerts?accountReference=SB-EUR-0001&severity=MEDIUM").header("Authorization", analyst))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/fraud-alerts?severity=EXTREME").header("Authorization", analyst))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/fraud-alerts").header("Authorization", bearer("alice")))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/fraud-alerts").header("Authorization", bearer("bob")))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void onlyAnalystsReviewAlertsAndOnlyOnce() throws Exception {
        JsonNode transfer = read(transfer(bearer("alice"), "SB-EUR-0002", "SB-EUR-0001", "2500.00"));
        String alertId = transfer.get("alerts").get(0).get("id").asText();
        String review = json(Map.of("decision", "CONFIRMED", "note", "Customer confirmed by phone"));

        mvc.perform(patch("/api/fraud-alerts/" + alertId + "/review").header("Authorization", bearer("alice"))
                        .contentType(MediaType.APPLICATION_JSON).content(review))
                .andExpect(status().isForbidden());

        String analyst = bearer("analyst");
        mvc.perform(patch("/api/fraud-alerts/" + alertId + "/review").header("Authorization", analyst)
                        .contentType(MediaType.APPLICATION_JSON).content(review))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.reviewNote").value("Customer confirmed by phone"))
                .andExpect(jsonPath("$.reviewedAt").isNotEmpty());

        mvc.perform(patch("/api/fraud-alerts/" + alertId + "/review").header("Authorization", analyst)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("decision", "DISMISSED"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALERT_ALREADY_REVIEWED"));
        mvc.perform(patch("/api/fraud-alerts/" + alertId + "/review").header("Authorization", analyst)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("decision", "OPEN"))))
                .andExpect(status().isBadRequest());

        assertThat(count("SELECT COUNT(*) FROM audit_event WHERE event_type = 'FRAUD_ALERT_REVIEWED'")).isEqualTo(1);
    }
}
