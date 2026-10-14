package com.smartbanking.security;

import com.smartbanking.entity.Role;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/** The authenticated caller, derived from a validated JWT. */
public record CurrentUser(Long id, String username, Role role) {

    public static CurrentUser from(Jwt jwt) {
        Object uid = jwt.getClaim(JwtService.USER_ID_CLAIM);
        List<String> roles = jwt.getClaimAsStringList(JwtService.ROLES_CLAIM);
        if (!(uid instanceof Number number) || roles == null || roles.isEmpty()) {
            throw new IllegalStateException("Token is missing required claims");
        }
        return new CurrentUser(number.longValue(), jwt.getSubject(), Role.valueOf(roles.get(0)));
    }

    public boolean isAnalyst() {
        return role == Role.ANALYST;
    }
}
