package dev.settle.group;

import dev.settle.TestcontainersConfiguration;
import dev.settle.user.User;
import dev.settle.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RotationCursorConcurrencyTest {

    @Autowired
    private GroupRepository groups;

    @Autowired
    private UserRepository users;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private UUID userId;
    private UUID groupId;

    @BeforeEach
    void createGroup() {
        User owner = users.save(new User(UUID.randomUUID() + "@example.com", "Owner"));
        Group group = groups.save(new Group("Concurrent", "PLN", owner));
        userId = owner.getId();
        groupId = group.getId();
    }

    @AfterEach
    void cleanUp() {
        groups.deleteById(groupId);
        users.deleteById(userId);
    }

    @Test
    void simultaneousExpensesNeverReadTheSameCursor() throws Exception {
        int threads = 8;
        int expenses = 40;
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);

        try {
            List<Future<Long>> cursorsSeen = new ArrayList<>();
            for (int i = 0; i < expenses; i++) {
                cursorsSeen.add(pool.submit(() -> {
                    go.await(); // start everyone at the same moment to maximise contention
                    return transaction.execute(status -> {
                        Group locked = groups.findByIdForUpdate(groupId).orElseThrow();
                        long seen = locked.getRotationCursor();
                        groups.advanceRotationCursor(groupId, 1);
                        return seen;
                    });
                }));
            }
            go.countDown();

            Set<Long> distinct = new HashSet<>();
            for (Future<Long> future : cursorsSeen) {
                distinct.add(future.get(30, TimeUnit.SECONDS));
            }

            assertThat(distinct).containsExactlyInAnyOrderElementsOf(
                    LongStream.range(0, expenses).boxed().toList());
            assertThat(groups.findById(groupId).orElseThrow().getRotationCursor()).isEqualTo(expenses);
        } finally {
            pool.shutdownNow();
        }
    }
}
