package dev.settle.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUserTest {

    private static JwtAuthenticationToken tokenWithSubject(String subject) {
        Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "none")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).claim("scope", "x");
        if (subject != null) {
            builder.subject(subject);
        }
        return new JwtAuthenticationToken(builder.build(), List.of());
    }

    @Test
    void readsTheUserIdFromTheTokenSubject() {
        UUID id = UUID.randomUUID();

        assertThat(CurrentUser.from(tokenWithSubject(id.toString())).id()).isEqualTo(id);
    }

    @Test
    void rejectsASubjectThatIsNotAUserId() {
        assertThatThrownBy(() -> CurrentUser.from(tokenWithSubject("admin")))
                .isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> CurrentUser.from(tokenWithSubject(null)))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void rejectsAnyAuthenticationThatIsNotABearerJwt() {
        assertThatThrownBy(() -> CurrentUser.from(null))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThatThrownBy(() -> CurrentUser.from(
                new UsernamePasswordAuthenticationToken(UUID.randomUUID().toString(), "x", AuthorityUtils.NO_AUTHORITIES)))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThatThrownBy(() -> CurrentUser.from(
                new AnonymousAuthenticationToken("key", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"))))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }
}
