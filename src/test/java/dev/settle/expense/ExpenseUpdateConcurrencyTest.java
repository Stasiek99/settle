package dev.settle.expense;

import dev.settle.TestcontainersConfiguration;
import dev.settle.common.PreconditionFailedException;
import dev.settle.group.Group;
import dev.settle.group.GroupRepository;
import dev.settle.group.Membership;
import dev.settle.group.MembershipRepository;
import dev.settle.group.MembershipRole;
import dev.settle.user.User;
import dev.settle.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

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
class ExpenseUpdateConcurrencyTest {

    @Autowired
    private ExpenseService service;

    @Autowired
    private ExpenseRepository expenses;

    @Autowired
    private MembershipRepository memberships;

    @Autowired
    private GroupRepository groups;

    @Autowired
    private UserRepository users;

    private User owner;
    private User friend;
    private UUID groupId;
    private UUID expenseId;

    @BeforeEach
    void createAnExpense() {
        owner = users.save(new User(UUID.randomUUID() + "@example.com", "Owner"));
        friend = users.save(new User(UUID.randomUUID() + "@example.com", "Friend"));
        Group group = groups.save(new Group("Concurrent", "PLN", owner));
        groupId = group.getId();
        memberships.save(new Membership(group, owner, MembershipRole.OWNER));
        memberships.save(new Membership(group, friend, MembershipRole.MEMBER));
        expenseId = service.create(groupId, request("Original", 1000)).id();
    }

    @AfterEach
    void cleanUp() {
        expenses.findAll().stream()
                .filter(e -> e.getGroup().getId().equals(groupId))
                .forEach(expenses::delete);
        memberships.findAll().stream()
                .filter(m -> m.getGroup().getId().equals(groupId))
                .forEach(memberships::delete);
        groups.deleteById(groupId);
        users.deleteById(owner.getId());
        users.deleteById(friend.getId());
    }

    private CreateExpenseRequest request(String description, long amount) {
        return new CreateExpenseRequest(description, amount, "PLN", owner.getId(), SplitType.EQUAL, null,
                List.of(new CreateExpenseRequest.Participant(owner.getId(), null, null),
                        new CreateExpenseRequest.Participant(friend.getId(), null, null)));
    }

    @Test
    void ofTwoSimultaneousEditsOfTheSameVersionExactlyOneWins() throws Exception {
        int contenders = 2;
        ExecutorService pool = Executors.newFixedThreadPool(contenders);
        CountDownLatch ready = new CountDownLatch(contenders);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < contenders; i++) {
                String description = "Winner " + i;
                Callable<String> edit = () -> {
                    ready.countDown();
                    go.await();
                    try {
                        service.update(groupId, expenseId, owner.getId(), "\"0\"", request(description, 2000));
                        return description;
                    } catch (PreconditionFailedException | ObjectOptimisticLockingFailureException e) {
                        return null;
                    }
                };
                results.add(pool.submit(edit));
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
            ExpenseResponse stored = service.get(groupId, expenseId);
            assertThat(stored.description()).isEqualTo(winners.get(0));
            assertThat(stored.version()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void anEditAndADeleteOfTheSameVersionCannotBothSucceed() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Callable<Boolean> edit = () -> {
                ready.countDown();
                go.await();
                try {
                    service.update(groupId, expenseId, owner.getId(), "\"0\"", request("Edited", 2000));
                    return true;
                } catch (PreconditionFailedException | ObjectOptimisticLockingFailureException e) {
                    return false;
                }
            };
            Callable<Boolean> delete = () -> {
                ready.countDown();
                go.await();
                try {
                    service.delete(groupId, expenseId, owner.getId(), "\"0\"");
                    return true;
                } catch (PreconditionFailedException | ObjectOptimisticLockingFailureException e) {
                    return false;
                }
            };
            Future<Boolean> edited = pool.submit(edit);
            Future<Boolean> deleted = pool.submit(delete);
            ready.await();
            go.countDown();

            boolean editSucceeded = edited.get(30, TimeUnit.SECONDS);
            boolean deleteSucceeded = deleted.get(30, TimeUnit.SECONDS);

            assertThat(editSucceeded && deleteSucceeded).isFalse();
            assertThat(editSucceeded || deleteSucceeded).isTrue();
            assertThat(expenses.findById(expenseId).isPresent()).isEqualTo(editSucceeded);
        } finally {
            pool.shutdownNow();
        }
    }
}
