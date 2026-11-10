package dev.settle.auth;

import dev.settle.logging.MdcKeys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

class MdcUserFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            String userId = asUserId(token.getToken().getSubject());
            if (userId != null) {
                MDC.put(MdcKeys.USER_ID, userId);
            }
        }
        chain.doFilter(request, response);
    }

    private static String asUserId(String subject) {
        try {
            return UUID.fromString(subject).toString();
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }
}
