package com.smartbanking.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** The application refuses to start without a JWT secret of at least 32 characters (256 bits for HS256). */
@Validated
@ConfigurationProperties("app.security")
public record SecurityProperties(
        @NotBlank(message = "app.security.jwt-secret must be set (environment variable JWT_SECRET)")
        @Size(min = 32, message = "app.security.jwt-secret must be at least 32 characters")
        String jwtSecret,
        @NotBlank String jwtIssuer,
        @NotNull Duration tokenTtl) {
}
