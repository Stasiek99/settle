package dev.settle.support;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.test.context.TestSecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TestAuth {

    private TestAuth() {
    }

    public static void authenticateAs(UUID userId) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .build();
        TestSecurityContextHolder.setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }
}
