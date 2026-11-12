package dev.settle.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
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

    private record Failures(int count, long windowEndsAtNanos) {

        Failures next() {
            return new Failures(count + 1, windowEndsAtNanos);
        }
    }

    private final LoginRateLimitProperties properties;
    private final Ticker ticker;
    private final Cache<String, Failures> failures;

    public LoginRateLimiter(LoginRateLimitProperties properties, Ticker ticker) {
        this.properties = properties;
        this.ticker = ticker;
        this.failures = Caffeine.newBuilder()
                .maximumSize(properties.maxTrackedKeys())
                .ticker(ticker)
                .expireAfter(new Expiry<String, Failures>() {
                    @Override
                    public long expireAfterCreate(String key, Failures value, long currentTime) {
                        return remaining(value, currentTime);
                    }

                    @Override
                    public long expireAfterUpdate(String key, Failures value, long currentTime, long currentDuration) {
                        return remaining(value, currentTime);
                    }

                    @Override
                    public long expireAfterRead(String key, Failures value, long currentTime, long currentDuration) {
                        return currentDuration;
                    }

                    private long remaining(Failures value, long currentTime) {
                        return Math.max(1, value.windowEndsAtNanos() - currentTime);
                    }
                })
                .build();
    }

    public void requireAllowed(String clientIp, String email) {
        long now = ticker.read();
        long longestWait = 0;
        for (Limit limit : limitsFor(clientIp, User.normalizeEmail(email))) {
            Failures recorded = failures.getIfPresent(limit.key());
            if (recorded != null && recorded.count() >= limit.max()) {
                longestWait = Math.max(longestWait, recorded.windowEndsAtNanos() - now);
            }
        }
        if (longestWait > 0) {
            log.atWarn().addKeyValue("client.ip", clientIp).log("Login blocked: too many failed attempts");
            throw new TooManyRequestsException(Duration.ofNanos(longestWait));
        }
    }

    public void recordFailure(String clientIp, String email) {
        long windowEndsAt = ticker.read() + properties.window().toNanos();
        for (Limit limit : limitsFor(clientIp, User.normalizeEmail(email))) {
            failures.asMap().merge(limit.key(), new Failures(1, windowEndsAt), (current, fresh) -> current.next());
        }
    }

    public void recordSuccess(String clientIp, String email) {
        failures.invalidate(pairKey(clientIp, User.normalizeEmail(email)));
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
