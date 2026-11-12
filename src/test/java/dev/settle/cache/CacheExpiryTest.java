package dev.settle.cache;

import com.github.benmanes.caffeine.cache.Ticker;
import dev.settle.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import({TestcontainersConfiguration.class, CacheExpiryTest.FakeClock.class})
class CacheExpiryTest {

    private static final AtomicLong NANOS = new AtomicLong();

    @TestConfiguration
    static class FakeClock {

        @Bean
        @Primary
        Ticker fakeTicker() {
            return NANOS::get;
        }
    }

    @Autowired
    private CacheManager caches;

    @Autowired
    private CacheProperties properties;

    @Test
    void balancesExpireAfterTheirConfiguredTtl() {
        var cache = caches.getCache(CacheNames.BALANCES);
        cache.put("key", "value");
        Duration ttl = properties.balances().ttl();

        NANOS.addAndGet(ttl.toNanos() - 1);
        assertThat(cache.get("key")).isNotNull();

        NANOS.addAndGet(2);
        assertThat(cache.get("key")).isNull();
    }

    @Test
    void membershipsOutliveBalancesBecauseTheirTtlIsLonger() {
        assertThat(properties.memberships().ttl()).isGreaterThan(properties.balances().ttl());
    }
}
