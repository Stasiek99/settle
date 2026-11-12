package dev.settle.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.util.List;

@Configuration
@EnableConfigurationProperties(CacheProperties.class)
@EnableCaching(order = Ordered.LOWEST_PRECEDENCE - 10)
class CacheConfig {

    @Bean
    Ticker cacheTicker() {
        return Ticker.systemTicker();
    }

    @Bean
    CacheManager cacheManager(CacheProperties properties, Ticker ticker) {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                build(CacheNames.MEMBERSHIPS, properties.memberships(), ticker),
                build(CacheNames.BALANCES, properties.balances(), ticker),
                build(CacheNames.SUGGESTIONS, properties.suggestions(), ticker)));
        return manager;
    }

    private static CaffeineCache build(String name, CacheProperties.Spec spec, Ticker ticker) {
        return new CaffeineCache(name, Caffeine.newBuilder()
                .expireAfterWrite(spec.ttl())
                .maximumSize(spec.maxSize())
                .ticker(ticker)
                .recordStats()
                .build());
    }
}
