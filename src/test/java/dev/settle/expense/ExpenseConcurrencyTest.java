package dev.settle.expense;

import dev.settle.TestcontainersConfiguration;
import dev.settle.expense.CreateExpenseRequest.Participant;
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
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ExpenseConcurrencyTest {

    @Autowired
    private ExpenseService expenseService;

    @Autowired
    private ExpenseRepository expenses;

    @Autowired
    private GroupRepository groups;

    @Autowired
    private MembershipRepository memberships;

    @Autowired
    private UserRepository users;

    @Autowired
    private JdbcTemplate jdbc;

    private List<UUID> userIds;
    private UUID groupId;

    @BeforeEach
    void createGroupWithThreeMembers() {
        String suffix = UUID.randomUUID().toString();
        List<User> members = List.of(
                users.save(new User("ann-" + suffix + "@example.com", "Ann")),
                users.save(new User("bob-" + suffix + "@example.com", "Bob")),
                users.save(new User("cid-" + suffix + "@example.com", "Cid")));
        Group group = groups.save(new Group("Concurrent", "PLN", members.getFirst()));
        for (User member : members) {
            memberships.save(new Membership(group, member, MembershipRole.MEMBER));
        }
        userIds = members.stream().map(User::getId).toList();
        groupId = group.getId();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from expenses where group_id = ?", groupId);
        groups.deleteById(groupId);
        users.deleteAllById(userIds);
    }

    @Test
    void everyoneEndsUpPayingTheSameAfterManySimultaneousExpenses() throws Exception {
        int threads = 8;
        int expensesToAdd = 24;
        List<Participant> everyone = userIds.stream().map(id -> new Participant(id, null, null)).toList();
        var request = new CreateExpenseRequest("Pizza", 1000L, "PLN", userIds.getFirst(),
                SplitType.EQUAL, null, everyone);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<ExpenseResponse>> futures = new ArrayList<>();
            for (int i = 0; i < expensesToAdd; i++) {
                futures.add(pool.submit(() -> {
                    go.await();
                    return expenseService.create(groupId, request);
                }));
            }
            go.countDown();
            for (Future<ExpenseResponse> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        Map<UUID, Long> paid = new HashMap<>();
        for (Expense expense : expenses.findAllByGroupIdOrderByExpenseDateDescCreatedAtDesc(groupId)) {
            expense.getSplits().forEach(s -> paid.merge(s.getUser().getId(), s.getAmountMinor(), Long::sum));
        }

        assertThat(paid).hasSize(3);
        assertThat(paid.values()).containsOnly(8000L);
        assertThat(groups.findById(groupId).orElseThrow().getRotationCursor()).isEqualTo(expensesToAdd);
    }
}
