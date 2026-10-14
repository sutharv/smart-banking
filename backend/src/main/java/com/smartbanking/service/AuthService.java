package com.smartbanking.service;

import com.smartbanking.dto.LoginRequest;
import com.smartbanking.dto.LoginResponse;
import com.smartbanking.entity.AuditEventType;
import com.smartbanking.exception.ApiException;
import com.smartbanking.repository.UserRepository;
import com.smartbanking.security.AppUserPrincipal;
import com.smartbanking.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public AuthService(AuthenticationManager authenticationManager, JwtService jwtService,
                       UserRepository userRepository, AuditService auditService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    public LoginResponse login(LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
        } catch (AuthenticationException ex) {
            // The same message for unknown users, wrong passwords and disabled accounts avoids user enumeration.
            Long knownUserId = userRepository.findByUsername(request.username()).map(u -> u.getId()).orElse(null);
            auditService.record(knownUserId, AuditEventType.LOGIN_FAILED, knownUserId == null ? null : "user:" + knownUserId);
            log.info("Failed login attempt{}", knownUserId == null ? " for unknown user" : " for user id " + knownUserId);
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "Invalid username or password.");
        }
        AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
        JwtService.IssuedToken token = jwtService.issue(principal);
        auditService.record(principal.getId(), AuditEventType.LOGIN_SUCCEEDED, "user:" + principal.getId());
        return new LoginResponse(token.value(), "Bearer", token.expiresAt(), principal.getUsername(), principal.getRole());
    }
}
