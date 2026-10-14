package com.smartbanking.security;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** Issues short-lived HS256 access tokens. Tokens are never logged. */
@Service
public class JwtService {

    public static final String USER_ID_CLAIM = "uid";
    public static final String ROLES_CLAIM = "roles";

    private final JwtEncoder encoder;
    private final SecurityProperties properties;
    private final Clock clock;

    public JwtService(JwtEncoder encoder, SecurityProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedToken issue(AppUserPrincipal principal) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.tokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.jwtIssuer())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(principal.getUsername())
                .claim(USER_ID_CLAIM, principal.getId())
                .claim(ROLES_CLAIM, List.of(principal.getRole().name()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }

    public record IssuedToken(String value, Instant expiresAt) {
    }
}
