package dev.settle.cache;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

@Component
public class GroupCacheEvictor {

    private static final List<String> GROUP_DATA_CACHES = List.of(CacheNames.BALANCES, CacheNames.SUGGESTIONS);

    private final CacheManager caches;

    public GroupCacheEvictor(CacheManager caches) {
        this.caches = caches;
    }

    public void evictGroupData(UUID groupId) {
        evictNow(groupId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictNow(groupId);
                }
            });
        }
    }

    private void evictNow(UUID groupId) {
        for (String name : GROUP_DATA_CACHES) {
            Cache cache = caches.getCache(name);
            if (cache != null) {
                cache.evict(groupId);
            }
        }
    }
}
