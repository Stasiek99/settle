package dev.settle.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.Ticker;

import java.time.Duration;

class FixedWindowCounter {

    private record Window(int count, long endsAtNanos) {

        Window next() {
            return new Window(count + 1, endsAtNanos);
        }
    }

    private final Duration window;
    private final Ticker ticker;
    private final Cache<String, Window> windows;

    FixedWindowCounter(Duration window, long maxTrackedKeys, Ticker ticker) {
        this.window = window;
        this.ticker = ticker;
        this.windows = Caffeine.newBuilder()
                .maximumSize(maxTrackedKeys)
                .ticker(ticker)
                .expireAfter(new Expiry<String, Window>() {
                    @Override
                    public long expireAfterCreate(String key, Window value, long currentTime) {
                        return remaining(value, currentTime);
                    }

                    @Override
                    public long expireAfterUpdate(String key, Window value, long currentTime, long currentDuration) {
                        return remaining(value, currentTime);
                    }

                    @Override
                    public long expireAfterRead(String key, Window value, long currentTime, long currentDuration) {
                        return currentDuration;
                    }

                    private long remaining(Window value, long currentTime) {
                        return Math.max(1, value.endsAtNanos() - currentTime);
                    }
                })
                .build();
    }

    long nanosUntilFree(String key, int max) {
        Window recorded = windows.getIfPresent(key);
        if (recorded == null || recorded.count() < max) {
            return 0;
        }
        return Math.max(0, recorded.endsAtNanos() - ticker.read());
    }

    void increment(String key) {
        long endsAt = ticker.read() + window.toNanos();
        windows.asMap().merge(key, new Window(1, endsAt), (current, fresh) -> current.next());
    }

    void reset(String key) {
        windows.invalidate(key);
    }
}
