package dev.settle.auth;

import dev.settle.common.AuthenticationFailedException;
import dev.settle.common.ConflictException;
import dev.settle.common.InvalidRequestException;
import dev.settle.support.TestEntities;
import dev.settle.user.User;
import dev.settle.user.UserRepository;
import dev.settle.user.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final TokenResponse TOKEN = new TokenResponse("jwt", "Bearer", 900);
    private static final RefreshTokenService.Issued REFRESH =
            new RefreshTokenService.Issued("r".repeat(43), Instant.parse("2026-11-17T10:00:00Z"));

    @Mock
    private UserRepository users;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokens;

    private PasswordEncoder encoder;
    private AuthService service;

    @BeforeEach
    void setUp() {
        encoder = spy(new BCryptPasswordEncoder(4));
        service = new AuthService(users, encoder, jwtService, refreshTokens);
    }

    private User annWithPassword(String password) {
        return TestEntities.withId(new User("ann@example.com", "Ann", encoder.encode(password)));
    }

    @Test
    void registerStoresAHashAndNeverThePassword() {
        when(users.existsByEmail("ann@example.com")).thenReturn(false);
        when(users.save(any(User.class))).thenAnswer(inv -> TestEntities.withId(inv.getArgument(0)));

        UserResponse response = service.register(new RegisterRequest("Ann@Example.com", " Ann ", "correct-horse"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("ann@example.com");
        assertThat(saved.getValue().getDisplayName()).isEqualTo("Ann");
        assertThat(saved.getValue().getPasswordHash()).isNotEqualTo("correct-horse");
        assertThat(encoder.matches("correct-horse", saved.getValue().getPasswordHash())).isTrue();
        assertThat(response.email()).isEqualTo("ann@example.com");
    }

    @Test
    void registerRejectsADuplicateEmailAfterNormalising() {
        when(users.existsByEmail("ann@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterRequest("  ANN@Example.com", "Ann", "correct-horse")))
                .isInstanceOf(ConflictException.class);

        verify(users, never()).save(any());
    }

    @Test
    void registerRejectsAPasswordLongerThan72BytesEvenIfItIsShortInCharacters() {
        String multiByte = "ą".repeat(40);

        assertThatThrownBy(() -> service.register(new RegisterRequest("ann@example.com", "Ann", multiByte)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("72 bytes");

        verify(users, never()).save(any());
    }

    @Test
    void loginReturnsATokenForTheRightPassword() {
        User ann = annWithPassword("correct-horse");
        when(users.findByEmail("ann@example.com")).thenReturn(Optional.of(ann));
        when(jwtService.issueFor(ann)).thenReturn(TOKEN);
        when(refreshTokens.startSession(ann.getId())).thenReturn(REFRESH);

        AuthService.Session session = service.login(new LoginRequest("ann@example.com", "correct-horse"));

        assertThat(session.access()).isEqualTo(TOKEN);
        assertThat(session.refresh()).isEqualTo(REFRESH);
        assertThat(session.user().email()).isEqualTo("ann@example.com");
    }

    @Test
    void loginNormalisesTheEmail() {
        User ann = annWithPassword("correct-horse");
        when(users.findByEmail("ann@example.com")).thenReturn(Optional.of(ann));
        when(jwtService.issueFor(ann)).thenReturn(TOKEN);
        when(refreshTokens.startSession(ann.getId())).thenReturn(REFRESH);

        assertThat(service.login(new LoginRequest("  ANN@Example.com ", "correct-horse")).access()).isEqualTo(TOKEN);
    }

    @Test
    void loginRejectsTheWrongPassword() {
        User ann = annWithPassword("correct-horse");
        when(users.findByEmail("ann@example.com")).thenReturn(Optional.of(ann));

        assertThatThrownBy(() -> service.login(new LoginRequest("ann@example.com", "wrong-password")))
                .isInstanceOf(AuthenticationFailedException.class);

        verifyNoInteractions(jwtService, refreshTokens);
    }

    @Test
    void loginForAnUnknownEmailFailsTheSameWayAndStillSpendsTheHashingTime() {
        when(users.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
        org.mockito.Mockito.clearInvocations(encoder);

        assertThatThrownBy(() -> service.login(new LoginRequest("nobody@example.com", "whatever-password")))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Invalid email or password");

        verify(encoder).matches(anyString(), anyString());
        verifyNoInteractions(jwtService);
    }

    @Test
    void aUserWithoutAPasswordCanNeverLogIn() {
        User legacy = TestEntities.user("ann@example.com", "Ann");
        when(users.findByEmail("ann@example.com")).thenReturn(Optional.of(legacy));

        assertThatThrownBy(() -> service.login(new LoginRequest("ann@example.com", "password-for-users-that-do-not-exist")))
                .isInstanceOf(AuthenticationFailedException.class);

        verifyNoInteractions(jwtService);
    }

    @Test
    void loginRejectsAPasswordLongerThan72BytesWithoutLookingAnythingUp() {
        assertThatThrownBy(() -> service.login(new LoginRequest("ann@example.com", "ą".repeat(40))))
                .isInstanceOf(AuthenticationFailedException.class);

        verifyNoInteractions(users, jwtService);
    }

    @Test
    void refreshRotatesTheTokenAndIssuesAFreshAccessToken() {
        User ann = annWithPassword("correct-horse");
        when(refreshTokens.rotate("old-token")).thenReturn(new RefreshTokenService.Rotation(ann.getId(), REFRESH));
        when(users.findById(ann.getId())).thenReturn(Optional.of(ann));
        when(jwtService.issueFor(ann)).thenReturn(TOKEN);

        AuthService.Session session = service.refresh("old-token");

        assertThat(session.access()).isEqualTo(TOKEN);
        assertThat(session.refresh()).isEqualTo(REFRESH);
        assertThat(session.user().id()).isEqualTo(ann.getId());
    }

    @Test
    void refreshFailsWhenTheTokenIsRejected() {
        when(refreshTokens.rotate("bad")).thenThrow(new AuthenticationFailedException("The session has ended."));

        assertThatThrownBy(() -> service.refresh("bad")).isInstanceOf(AuthenticationFailedException.class);

        verifyNoInteractions(jwtService);
    }

    @Test
    void refreshFailsWhenTheUserNoLongerExists() {
        UUID gone = UUID.randomUUID();
        when(refreshTokens.rotate("old-token")).thenReturn(new RefreshTokenService.Rotation(gone, REFRESH));
        when(users.findById(gone)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refresh("old-token")).isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void logoutRevokesTheSession() {
        service.logout("some-token");

        verify(refreshTokens).revoke("some-token");
    }
}
