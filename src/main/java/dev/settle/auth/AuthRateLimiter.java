package dev.settle.auth;

import com.github.benmanes.caffeine.cache.Ticker;
import dev.settle.common.TooManyRequestsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AuthRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimiter.class);

    private final AuthRateLimitProperties properties;
    private final FixedWindowCounter registrations;
    private final FixedWindowCounter refreshes;

    public AuthRateLimiter(AuthRateLimitProperties properties, Ticker ticker) {
        this.properties = properties;
        this.registrations = new FixedWindowCounter(
                properties.register().window(), properties.maxTrackedKeys(), ticker);
        this.refreshes = new FixedWindowCounter(
                properties.refresh().window(), properties.maxTrackedKeys(), ticker);
    }

    public void countRegistration(String clientIp) {
        count(registrations, properties.register().maxPerClient(), clientIp, "Too many registrations");
    }

    public void countRefresh(String clientIp) {
        count(refreshes, properties.refresh().maxPerClient(), clientIp, "Too many session refreshes");
    }

    private static void count(FixedWindowCounter counter, int max, String clientIp, String reason) {
        long wait = counter.nanosUntilFree(clientIp, max);
        if (wait > 0) {
            log.atWarn().addKeyValue("client.ip", clientIp).log("Request blocked: {}", reason);
            throw new TooManyRequestsException(reason, Duration.ofNanos(wait));
        }
        counter.increment(clientIp);
    }
}
