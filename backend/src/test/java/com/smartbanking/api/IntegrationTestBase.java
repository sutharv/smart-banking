package com.smartbanking.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack tests: real HTTP layer (MockMvc), real security, real PostgreSQL via Testcontainers.
 * Skipped automatically when Docker is not available; CI always runs them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
@Sql(scripts = "/sql/reset-and-seed.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
abstract class IntegrationTestBase {

    protected static final String PASSWORD = "Demo!2026";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbc;

    protected String bearer(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(body).get("accessToken").asText();
    }

    protected ResultActions transfer(String token, String source, String destination, String amount,
                                     String currency, String idempotencyKey) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sourceAccountReference", source);
        body.put("destinationAccountReference", destination);
        body.put("amount", new BigDecimal(amount));
        body.put("currency", currency);
        body.put("reference", "test transfer");
        var request = post("/api/transfers")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body));
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return mvc.perform(request);
    }

    protected ResultActions transfer(String token, String source, String destination, String amount) throws Exception {
        return transfer(token, source, destination, amount, "EUR", null);
    }

    protected BigDecimal balance(String reference) {
        return jdbc.queryForObject("SELECT balance FROM account WHERE account_reference = ?", BigDecimal.class, reference);
    }

    protected int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    protected JsonNode read(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
