package dev.settle.auth;

import dev.settle.common.AuthenticationFailedException;
import dev.settle.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Instant START = Instant.parse("2026-11-10T10:00:00Z");
    private static final SessionProperties PROPERTIES =
            new SessionProperties(Duration.ofDays(7), Duration.ofSeconds(10), "/api", "/api/auth", true);

    @Mock
    private RefreshTokenRepository repository;

    private final Map<String, RefreshToken> stored = new HashMap<>();
    private final Set<UUID> revokedFamilies = new HashSet<>();
    private MutableClock clock;
    private RefreshTokenService service;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        clock = new MutableClock(START);
        service = new RefreshTokenService(repository, PROPERTIES, clock);
        lenient().when(repository.save(any(RefreshToken.class))).thenAnswer(invocation -> {
            RefreshToken token = invocation.getArgument(0);
            stored.put(token.getTokenHash(), token);
            return token;
        });
        lenient().when(repository.findByTokenHashForUpdate(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get(invocation.<String>getArgument(0))));
        lenient().when(repository.findByTokenHash(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get(invocation.<String>getArgument(0))));
        lenient().when(repository.revokeFamily(any(UUID.class), any(Instant.class))).thenAnswer(invocation -> {
            revokedFamilies.add(invocation.getArgument(0));
            return 1;
        });
    }

    private RefreshToken storedFor(RefreshTokenService.Issued issued) {
        return stored.get(RefreshTokenService.hashOf(issued.value()));
    }

    @Test
    void aNewSessionGetsAnOpaqueTokenThatIsStoredOnlyAsAHash() {
        RefreshTokenService.Issued issued = service.startSession(userId);

        assertThat(issued.value()).matches("^[A-Za-z0-9_-]{43}$");
        assertThat(stored).doesNotContainKey(issued.value());
        RefreshToken token = storedFor(issued);
        assertThat(token.getTokenHash()).hasSize(64).isNotEqualTo(issued.value());
        assertThat(token.getUserId()).isEqualTo(userId);
        assertThat(issued.expiresAt()).isEqualTo(START.plus(Duration.ofDays(7)));
    }

    @Test
    void everyTokenIsDifferent() {
        Set<String> values = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            values.add(service.startSession(userId).value());
        }

        assertThat(values).hasSize(50);
    }

    @Test
    void theIssuedTokenDoesNotShowUpInItsStringForm() {
        RefreshTokenService.Issued issued = service.startSession(userId);

        assertThat(issued.toString()).doesNotContain(issued.value());
    }

    @Test
    void rotatingGivesANewTokenInTheSameFamilyAndMarksTheOldOneUsed() {
        RefreshTokenService.Issued first = service.startSession(userId);
        clock.advance(Duration.ofMinutes(5));

        RefreshTokenService.Rotation rotation = service.rotate(first.value());

        assertThat(rotation.userId()).isEqualTo(userId);
        assertThat(rotation.next().value()).isNotEqualTo(first.value());
        assertThat(storedFor(rotation.next()).getFamilyId()).isEqualTo(storedFor(first).getFamilyId());
        assertThat(storedFor(first).getUsedAt()).isEqualTo(clock.instant());
        assertThat(rotation.next().expiresAt()).isEqualTo(clock.instant().plus(Duration.ofDays(7)));
    }

    @Test
    void aRotatedTokenSeenAgainRightAwayIsRejectedWithoutKillingTheSession() {
        RefreshTokenService.Issued first = service.startSession(userId);
        service.rotate(first.value());
        clock.advance(Duration.ofSeconds(3));

        assertThatThrownBy(() -> service.rotate(first.value())).isInstanceOf(AuthenticationFailedException.class);

        verify(repository, never()).revokeFamily(any(), any());
    }

    @Test
    void aRotatedTokenSeenAgainLaterMeansTheftAndRevokesTheWholeFamily() {
        RefreshTokenService.Issued first = service.startSession(userId);
        service.rotate(first.value());
        clock.advance(Duration.ofSeconds(11));

        assertThatThrownBy(() -> service.rotate(first.value())).isInstanceOf(AuthenticationFailedException.class);

        verify(repository).revokeFamily(eq(storedFor(first).getFamilyId()), eq(clock.instant()));
    }

    @Test
    void theGraceWindowEndsExactlyAfterTheConfiguredTime() {
        RefreshTokenService.Issued first = service.startSession(userId);
        service.rotate(first.value());

        clock.advance(Duration.ofSeconds(10));
        assertThatThrownBy(() -> service.rotate(first.value())).isInstanceOf(AuthenticationFailedException.class);
        verify(repository, never()).revokeFamily(any(), any());

        clock.advance(Duration.ofNanos(1));
        assertThatThrownBy(() -> service.rotate(first.value())).isInstanceOf(AuthenticationFailedException.class);
        verify(repository).revokeFamily(any(), any());
    }

    @Test
    void anExpiredTokenIsRejected() {
        RefreshTokenService.Issued first = service.startSession(userId);
        clock.advance(Duration.ofDays(7));

        assertThatThrownBy(() -> service.rotate(first.value())).isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void aTokenIsStillGoodJustBeforeItExpires() {
        RefreshTokenService.Issued first = service.startSession(userId);
        clock.advance(Duration.ofDays(7).minusSeconds(1));

        assertThat(service.rotate(first.value()).userId()).isEqualTo(userId);
    }

    @Test
    void aRevokedTokenIsRejected() {
        RefreshTokenService.Issued first = service.startSession(userId);
        RefreshToken token = storedFor(first);
        org.springframework.test.util.ReflectionTestUtils.setField(token, "revokedAt", clock.instant());

        assertThatThrownBy(() -> service.rotate(first.value())).isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void anUnknownTokenIsRejected() {
        assertThatThrownBy(() -> service.rotate("a".repeat(43))).isInstanceOf(AuthenticationFailedException.class);
    }

    static Stream<String> malformedTokens() {
        return Stream.of(null, "", "short", "has spaces in it that make it not a token at all!",
                "é".repeat(43), "a".repeat(44), "a".repeat(42), "a'; drop table refresh_tokens; --");
    }

    @ParameterizedTest
    @MethodSource("malformedTokens")
    void malformedTokensAreRejectedWithoutTouchingTheDatabase(String value) {
        assertThatThrownBy(() -> service.rotate(value)).isInstanceOf(AuthenticationFailedException.class);

        verify(repository, never()).findByTokenHashForUpdate(any());
    }

    @Test
    void revokingEndsTheWholeFamily() {
        RefreshTokenService.Issued first = service.startSession(userId);
        RefreshTokenService.Issued second = service.rotate(first.value()).next();

        service.revoke(second.value());

        verify(repository).revokeFamily(eq(storedFor(first).getFamilyId()), any());
    }

    @Test
    void revokingSomethingUnknownOrMalformedIsSilent() {
        service.revoke("a".repeat(43));
        service.revoke("not a token");
        service.revoke(null);

        verify(repository, never()).revokeFamily(any(), any());
    }

    @Test
    void purgingDeletesWhatExpiredBeforeNow() {
        when(repository.deleteExpiredBefore(clock.instant())).thenReturn(4);

        assertThat(service.purgeExpired()).isEqualTo(4);
    }

    @Test
    void theHashIsStableAndOneWay() {
        assertThat(RefreshTokenService.hashOf("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(RefreshTokenService.hashOf("abc")).isNotEqualTo(RefreshTokenService.hashOf("abd"));
    }
}
