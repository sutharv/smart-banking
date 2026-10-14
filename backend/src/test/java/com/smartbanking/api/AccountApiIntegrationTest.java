package com.smartbanking.api;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountApiIntegrationTest extends IntegrationTestBase {

    @Test
    void customerSeesOnlyOwnAccounts() throws Exception {
        mvc.perform(get("/api/accounts").header("Authorization", bearer("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].reference").value("SB-EUR-0001"))
                .andExpect(jsonPath("$[0].balance").value(5000.00))
                .andExpect(jsonPath("$[0].id").doesNotExist());
    }

    @Test
    void anotherCustomersAccountIsReportedAsNotFound() throws Exception {
        String alice = bearer("alice");
        mvc.perform(get("/api/accounts/SB-EUR-0004").header("Authorization", alice))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
        mvc.perform(get("/api/accounts/SB-EUR-0004/transactions").header("Authorization", alice))
                .andExpect(status().isNotFound());
    }

    @Test
    void analystHasReadOnlyAccessToAllAccounts() throws Exception {
        String analyst = bearer("analyst");
        mvc.perform(get("/api/accounts").header("Authorization", analyst))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
        mvc.perform(get("/api/accounts/SB-EUR-0004").header("Authorization", analyst))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Bob Everyday"));
    }

    @Test
    void historyIsPaginatedNewestFirst() throws Exception {
        String alice = bearer("alice");
        transfer(alice, "SB-EUR-0001", "SB-EUR-0004", "10.00").andExpect(status().isCreated());
        transfer(alice, "SB-EUR-0001", "SB-EUR-0004", "20.00").andExpect(status().isCreated());
        transfer(alice, "SB-EUR-0001", "SB-EUR-0004", "30.00").andExpect(status().isCreated());

        mvc.perform(get("/api/accounts/SB-EUR-0001/transactions?page=0&size=2").header("Authorization", alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].amount").value(30.00))
                .andExpect(jsonPath("$.content[0].direction").value("OUTGOING"))
                .andExpect(jsonPath("$.content[0].counterpartyReference").value("SB-EUR-0004"));

        mvc.perform(get("/api/accounts/SB-EUR-0004/transactions").header("Authorization", bearer("bob")))
                .andExpect(jsonPath("$.content[0].direction").value("INCOMING"))
                .andExpect(jsonPath("$.content[0].counterpartyReference").value("SB-EUR-0001"));
    }

    @Test
    void rejectedAttemptsAreVisibleToTheSenderButNotTheRecipient() throws Exception {
        transfer(bearer("alice"), "SB-EUR-0001", "SB-EUR-0004", "9999.00").andExpect(status().isUnprocessableEntity());

        mvc.perform(get("/api/accounts/SB-EUR-0001/transactions").header("Authorization", bearer("alice")))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.content[0].rejectionReason").value("INSUFFICIENT_FUNDS"));
        mvc.perform(get("/api/accounts/SB-EUR-0004/transactions").header("Authorization", bearer("bob")))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void invalidPaginationIsRejected() throws Exception {
        mvc.perform(get("/api/accounts/SB-EUR-0001/transactions?size=500").header("Authorization", bearer("alice")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAGINATION"));
        mvc.perform(get("/api/accounts/SB-EUR-0001/transactions?page=abc").header("Authorization", bearer("alice")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }
}
