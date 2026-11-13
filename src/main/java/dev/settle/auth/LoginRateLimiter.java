package dev.settle.auth;

import com.github.benmanes.caffeine.cache.Ticker;
import dev.settle.common.TooManyRequestsException;
import dev.settle.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class LoginRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimiter.class);

    private final LoginRateLimitProperties properties;
    private final FixedWindowCounter failures;

    public LoginRateLimiter(LoginRateLimitProperties properties, Ticker ticker) {
        this.properties = properties;
        this.failures = new FixedWindowCounter(properties.window(), properties.maxTrackedKeys(), ticker);
    }

    public void requireAllowed(String clientIp, String email) {
        long longestWait = 0;
        for (Limit limit : limitsFor(clientIp, User.normalizeEmail(email))) {
            longestWait = Math.max(longestWait, failures.nanosUntilFree(limit.key(), limit.max()));
        }
        if (longestWait > 0) {
            log.atWarn().addKeyValue("client.ip", clientIp).log("Login blocked: too many failed attempts");
            throw new TooManyRequestsException(Duration.ofNanos(longestWait));
        }
    }

    public void recordFailure(String clientIp, String email) {
        for (Limit limit : limitsFor(clientIp, User.normalizeEmail(email))) {
            failures.increment(limit.key());
        }
    }

    public void recordSuccess(String clientIp, String email) {
        failures.reset(pairKey(clientIp, User.normalizeEmail(email)));
    }

    private List<Limit> limitsFor(String clientIp, String account) {
        return List.of(
                new Limit(pairKey(clientIp, account), properties.maxPerClientAndAccount()),
                new Limit("client:" + clientIp, properties.maxPerClient()),
                new Limit("account:" + account, properties.maxPerAccount()));
    }

    private static String pairKey(String clientIp, String account) {
        return "pair:" + clientIp + "|" + account;
    }

    private record Limit(String key, int max) {
    }
}
