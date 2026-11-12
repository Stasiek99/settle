package dev.settle.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
class SessionCookies {

    static final String ACCESS = "settle_access";
    static final String REFRESH = "settle_refresh";

    private final SessionProperties properties;

    SessionCookies(SessionProperties properties) {
        this.properties = properties;
    }

    void write(HttpServletResponse response, TokenResponse access, RefreshTokenService.Issued refresh) {
        add(response, ACCESS, access.accessToken(), properties.apiPath(), Duration.ofSeconds(access.expiresIn()));
        add(response, REFRESH, refresh.value(), properties.authPath(), properties.refreshExpiration());
    }

    void clear(HttpServletResponse response) {
        add(response, ACCESS, "", properties.apiPath(), Duration.ZERO);
        add(response, REFRESH, "", properties.authPath(), Duration.ZERO);
    }

    Optional<String> accessToken(HttpServletRequest request) {
        return valueOf(request, ACCESS);
    }

    Optional<String> refreshToken(HttpServletRequest request) {
        return valueOf(request, REFRESH);
    }

    private void add(HttpServletResponse response, String name, String value, String path, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(properties.secureCookies())
                .sameSite("Strict")
                .path(path)
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private static Optional<String> valueOf(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (cookie.getName().equals(name) && !cookie.getValue().isEmpty()) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }
}
