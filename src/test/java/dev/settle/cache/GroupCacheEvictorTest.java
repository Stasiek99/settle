package dev.settle.cache;

import dev.settle.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class GroupCacheEvictorTest {

    @Autowired
    private GroupCacheEvictor evictor;

    @Autowired
    private CacheManager caches;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final UUID groupId = UUID.randomUUID();
    private final UUID otherGroupId = UUID.randomUUID();
    private Cache balances;
    private Cache suggestions;
    private Cache memberships;

    @BeforeEach
    void fillCaches() {
        balances = caches.getCache(CacheNames.BALANCES);
        suggestions = caches.getCache(CacheNames.SUGGESTIONS);
        memberships = caches.getCache(CacheNames.MEMBERSHIPS);
        balances.put(groupId, "balances");
        suggestions.put(groupId, "suggestions");
        balances.put(otherGroupId, "other");
        memberships.put(groupId + ":user", true);
    }

    @Test
    void evictsBothGroupDataCachesOfThatGroupOnly() {
        evictor.evictGroupData(groupId);

        assertThat(balances.get(groupId)).isNull();
        assertThat(suggestions.get(groupId)).isNull();
        assertThat(balances.get(otherGroupId)).isNotNull();
        assertThat(memberships.get(groupId + ":user")).isNotNull();
    }

    @Test
    void evictsAgainAfterCommitSoAConcurrentReaderCannotLeaveStaleDataBehind() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            evictor.evictGroupData(groupId);
            assertThat(balances.get(groupId)).isNull();

            balances.put(groupId, "stale value cached by a reader before the commit");
        });

        assertThat(balances.get(groupId)).isNull();
    }

    @Test
    void doesNotEvictAgainWhenTheTransactionRollsBack() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            evictor.evictGroupData(groupId);
            balances.put(groupId, "fresh value");
            status.setRollbackOnly();
        });

        assertThat(balances.get(groupId)).isNotNull();
    }
}
