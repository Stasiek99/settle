package dev.settle.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    private static final Logger log = LoggerFactory.getLogger("dev.settle.access");
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");
    private static final int MAX_LOGGED_PATH = 200;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = resolveRequestId(request.getHeader(REQUEST_ID_HEADER));
        MDC.put(MdcKeys.REQUEST_ID, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        long startedAt = System.nanoTime();
        boolean failed = false;
        try {
            chain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException | Error e) {
            failed = true;
            throw e;
        } finally {
            logAccess(request, response, failed, System.nanoTime() - startedAt);
            MDC.remove(MdcKeys.REQUEST_ID);
            MDC.remove(MdcKeys.USER_ID);
        }
    }

    private static void logAccess(HttpServletRequest request, HttpServletResponse response, boolean failed, long nanos) {
        String path = abbreviate(request.getRequestURI());
        if (path.startsWith("/actuator/health")) {
            return;
        }
        int status = failed ? 500 : response.getStatus();
        long millis = TimeUnit.NANOSECONDS.toMillis(nanos);
        Level level = status >= 500 ? Level.ERROR : Level.INFO;

        log.atLevel(level)
                .addKeyValue("http.request.method", request.getMethod())
                .addKeyValue("url.path", path)
                .addKeyValue("http.response.status_code", status)
                .addKeyValue("event.duration_ms", millis)
                .addKeyValue("client.ip", request.getRemoteAddr())
                .log("HTTP {} {} -> {} in {} ms", request.getMethod(), path, status, millis);
    }

    static String resolveRequestId(String inbound) {
        if (inbound != null && SAFE_REQUEST_ID.matcher(inbound).matches()) {
            return inbound;
        }
        return UUID.randomUUID().toString();
    }

    private static String abbreviate(String path) {
        return path.length() <= MAX_LOGGED_PATH ? path : path.substring(0, MAX_LOGGED_PATH) + "...";
    }
}
