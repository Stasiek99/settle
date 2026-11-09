package dev.settle.auth;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.UUID;

public record CurrentUser(UUID id) {

    public static CurrentUser from(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            throw new AuthenticationCredentialsNotFoundException("No authenticated user");
        }
        try {
            return new CurrentUser(UUID.fromString(token.getToken().getSubject()));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BadCredentialsException("Token subject is not a user id", e);
        }
    }

    public static CurrentUser fromSecurityContext() {
        return from(SecurityContextHolder.getContext().getAuthentication());
    }
}
