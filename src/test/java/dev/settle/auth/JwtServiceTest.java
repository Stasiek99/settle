package dev.settle.auth;

import dev.settle.support.TestEntities;
import dev.settle.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "a-test-secret-with-more-than-thirty-two-characters";
    private static final Duration ONE_HOUR = Duration.ofHours(1);

    private final SecurityConfig config = new SecurityConfig();
    private final User ann = TestEntities.user("ann@example.com", "Ann");

    private JwtService serviceWith(String secret, String issuer, Clock clock) {
        JwtProperties properties = new JwtProperties(secret, issuer, ONE_HOUR);
        return new JwtService(config.jwtEncoder(properties), properties, clock);
    }

    private JwtDecoder decoder() {
        return config.jwtDecoder(new JwtProperties(SECRET, "settle", ONE_HOUR));
    }

    @Test
    void issuedTokenCarriesTheUserAsSubjectAndExpiresAfterTheConfiguredTime() {
        TokenResponse response = serviceWith(SECRET, "settle", Clock.systemUTC()).issueFor(ann);

        Jwt jwt = decoder().decode(response.accessToken());

        assertThat(jwt.getSubject()).isEqualTo(ann.getId().toString());
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("settle");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(ONE_HOUR);
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600);
    }

    @Test
    void tokenIssuedLongAgoIsRejectedAsExpired() {
        Clock twoHoursAgo = Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC);
        String token = serviceWith(SECRET, "settle", twoHoursAgo).issueFor(ann).accessToken();

        assertThatThrownBy(() -> decoder().decode(token))
                .isInstanceOf(BadJwtException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String token = serviceWith("another-secret-with-more-than-thirty-two-chars", "settle", Clock.systemUTC())
                .issueFor(ann).accessToken();

        assertThatThrownBy(() -> decoder().decode(token)).isInstanceOf(BadJwtException.class);
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        String token = serviceWith(SECRET, "someone-else", Clock.systemUTC()).issueFor(ann).accessToken();

        assertThatThrownBy(() -> decoder().decode(token)).isInstanceOf(BadJwtException.class);
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = serviceWith(SECRET, "settle", Clock.systemUTC()).issueFor(ann).accessToken();
        String[] parts = token.split("\\.");
        String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"00000000-0000-0000-0000-000000000001\",\"iss\":\"settle\",\"exp\":9999999999}".getBytes());

        assertThatThrownBy(() -> decoder().decode(parts[0] + "." + forgedPayload + "." + parts[2]))
                .isInstanceOf(BadJwtException.class);
    }

    @Test
    void unsignedTokenIsRejected() {
        String header = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"alg\":\"none\"}".getBytes());
        String payload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"sub\":\"%s\",\"iss\":\"settle\",\"exp\":9999999999}".formatted(ann.getId())).getBytes());

        assertThatThrownBy(() -> decoder().decode(header + "." + payload + "."))
                .isInstanceOf(BadJwtException.class);
    }
}
