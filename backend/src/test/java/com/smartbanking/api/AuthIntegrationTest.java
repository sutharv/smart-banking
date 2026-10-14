package com.smartbanking.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

class AuthIntegrationTest extends IntegrationTestBase {

    @Test
    void validCredentialsReturnBearerToken() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "alice", "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());
        assertThat(count("SELECT COUNT(*) FROM audit_event WHERE event_type = 'LOGIN_SUCCEEDED' AND actor_user_id = 1")).isEqualTo(1);
    }

    @Test
    void wrongPasswordUnknownUserAndDisabledUserAllGetTheSameResponse() throws Exception {
        for (String username : new String[]{"alice", "nobody", "disabled"}) {
            String password = username.equals("alice") ? "wrong-password" : PASSWORD;
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("username", username, "password", password))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                    .andExpect(jsonPath("$.detail").value("Invalid username or password."));
        }
        assertThat(count("SELECT COUNT(*) FROM audit_event WHERE event_type = 'LOGIN_FAILED'")).isEqualTo(3);
    }

    @Test
    void loginRequestIsValidated() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "", "password", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void protectedEndpointsRequireAToken() throws Exception {
        mvc.perform(get("/api/accounts"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        String token = bearer("alice");
        String tampered = token.substring(0, token.length() - 4) + (token.endsWith("AAAA") ? "BBBB" : "AAAA");
        mvc.perform(get("/api/accounts").header("Authorization", tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthEndpointIsPublicButMinimal() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }
}
