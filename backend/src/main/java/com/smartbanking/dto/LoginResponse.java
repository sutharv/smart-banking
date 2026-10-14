package com.smartbanking.dto;

import com.smartbanking.entity.Role;

import java.time.Instant;

public record LoginResponse(String accessToken, String tokenType, Instant expiresAt, String username, Role role) {
}
