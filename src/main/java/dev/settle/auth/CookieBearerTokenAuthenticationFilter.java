package dev.settle.auth;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * Authenticates a request from the session cookie.
 *
 * <p>It is deliberately separate from the resource server's own filter. Spring Security exempts every
 * request that carries a bearer token (as seen by the configured resolver) from CSRF protection, which is
 * right for an Authorization header and wrong for a cookie the browser attaches by itself. This filter runs
 * after the CSRF check, so cookie-authenticated requests are still protected.
 */
final class CookieBearerTokenAuthenticationFilter extends BearerTokenAuthenticationFilter {

    CookieBearerTokenAuthenticationFilter(JwtDecoder decoder, SessionCookies cookies,
                                          AuthenticationEntryPoint entryPoint) {
        super(authenticationManager(decoder));
        setBearerTokenResolver(request -> {
            if (SecurityConfig.isAuthEndpoint(request) || request.getHeader("Authorization") != null) {
                return null;
            }
            return cookies.accessToken(request).orElse(null);
        });
        setAuthenticationEntryPoint(entryPoint);
    }

    private static AuthenticationManager authenticationManager(JwtDecoder decoder) {
        return new ProviderManager(new JwtAuthenticationProvider(decoder));
    }
}
