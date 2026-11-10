package dev.settle.group;

import dev.settle.TestcontainersConfiguration;
import dev.settle.common.PreconditionFailedException;
import dev.settle.user.User;
import dev.settle.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class GroupOptimisticLockingConcurrencyTest {

    @Autowired
    private GroupService service;

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
        userId = owner.getId();
        groupId = groups.save(new Group("Original", "PLN", owner)).getId();
    }

    @AfterEach
    void cleanUp() {
        groups.deleteById(groupId);
        users.deleteById(userId);
    }

    @Test
    void ofTwoSimultaneousRenamesWithTheSameVersionExactlyOneWins() throws Exception {
        int contenders = 2;
        ExecutorService pool = Executors.newFixedThreadPool(contenders);
        CountDownLatch ready = new CountDownLatch(contenders);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < contenders; i++) {
                String name = "Winner " + i;
                Callable<String> task = () -> {
                    ready.countDown();
                    go.await();
                    try {
                        service.rename(groupId, 0, new UpdateGroupRequest(name));
                        return name;
                    } catch (PreconditionFailedException | ObjectOptimisticLockingFailureException e) {
                        return null;
                    }
                };
                results.add(pool.submit(task));
            }
            ready.await();
            go.countDown();

            List<String> winners = new ArrayList<>();
            for (Future<String> result : results) {
                String winner = result.get(30, TimeUnit.SECONDS);
                if (winner != null) {
                    winners.add(winner);
                }
            }

            assertThat(winners).hasSize(1);
            GroupResponse stored = service.get(groupId);
            assertThat(stored.name()).isEqualTo(winners.get(0));
            assertThat(stored.version()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void advancingTheRotationCursorDoesNotChangeTheVersion() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> groups.advanceRotationCursor(groupId, 3));

        GroupResponse stored = service.get(groupId);

        assertThat(stored.version()).isZero();
        assertThat(groups.findById(groupId).orElseThrow().getRotationCursor()).isEqualTo(3);
    }

    @Test
    void aRenameAfterTheCursorMovedStillWorksWithTheSameETag() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> groups.advanceRotationCursor(groupId, 2));

        GroupResponse renamed = service.rename(groupId, 0, new UpdateGroupRequest("Renamed"));

        assertThat(renamed.version()).isEqualTo(1);
        assertThat(groups.findById(groupId).orElseThrow().getRotationCursor()).isEqualTo(2);
    }
}
