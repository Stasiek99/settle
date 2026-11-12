package dev.settle.auth;

import dev.settle.common.AuthenticationFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final int TOKEN_BYTES = 32;
    private static final Pattern WELL_FORMED = Pattern.compile("^[A-Za-z0-9_-]{43}$");
    private static final String SESSION_ENDED = "The session has ended. Please sign in again.";

    record Issued(String value, Instant expiresAt) {

        @Override
        public String toString() {
            return "Issued[value=***, expiresAt=" + expiresAt + "]";
        }
    }

    record Rotation(UUID userId, Issued next) {
    }

    private final RefreshTokenRepository tokens;
    private final SessionProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    RefreshTokenService(RefreshTokenRepository tokens, SessionProperties properties, Clock clock) {
        this.tokens = tokens;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    Issued startSession(UUID userId) {
        return issue(userId, UUID.randomUUID());
    }

    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    Rotation rotate(String rawToken) {
        RefreshToken current = tokens.findByTokenHashForUpdate(hashOf(requireWellFormed(rawToken)))
                .orElseThrow(() -> new AuthenticationFailedException(SESSION_ENDED));
        Instant now = clock.instant();

        if (current.isRevoked() || current.isExpired(now)) {
            throw new AuthenticationFailedException(SESSION_ENDED);
        }
        if (current.isUsed()) {
            if (!current.getUsedAt().plus(properties.refreshReuseGrace()).isBefore(now)) {
                log.atInfo().addKeyValue("session.family", current.getFamilyId())
                        .log("Refresh rejected: the token was rotated a moment ago");
            } else {
                tokens.revokeFamily(current.getFamilyId(), now);
                log.atWarn().addKeyValue("session.family", current.getFamilyId())
                        .addKeyValue("user.id", current.getUserId())
                        .log("Refresh token reuse detected; the whole session was revoked");
            }
            throw new AuthenticationFailedException(SESSION_ENDED);
        }

        current.markUsed(now);
        return new Rotation(current.getUserId(), issue(current.getUserId(), current.getFamilyId()));
    }

    @Transactional
    void revoke(String rawToken) {
        if (rawToken == null || !WELL_FORMED.matcher(rawToken).matches()) {
            return;
        }
        tokens.findByTokenHash(hashOf(rawToken))
                .ifPresent(token -> tokens.revokeFamily(token.getFamilyId(), clock.instant()));
    }

    @Transactional
    int purgeExpired() {
        return tokens.deleteExpiredBefore(clock.instant());
    }

    private Issued issue(UUID userId, UUID familyId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.refreshExpiration());
        tokens.save(new RefreshToken(userId, familyId, hashOf(value), now, expiresAt));
        return new Issued(value, expiresAt);
    }

    private static String requireWellFormed(String rawToken) {
        if (rawToken == null || !WELL_FORMED.matcher(rawToken).matches()) {
            throw new AuthenticationFailedException(SESSION_ENDED);
        }
        return rawToken;
    }

    static String hashOf(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
