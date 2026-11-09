package dev.settle.balance;

import dev.settle.TestcontainersConfiguration;
import dev.settle.expense.CreateExpenseRequest;
import dev.settle.expense.CreateExpenseRequest.Participant;
import dev.settle.expense.ExpenseResponse;
import dev.settle.expense.ExpenseService;
import dev.settle.expense.SplitType;
import dev.settle.group.Group;
import dev.settle.group.GroupRepository;
import dev.settle.group.Membership;
import dev.settle.group.MembershipRepository;
import dev.settle.group.MembershipRole;
import dev.settle.user.User;
import dev.settle.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class BalanceApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private GroupRepository groups;

    @Autowired
    private MembershipRepository memberships;

    @Autowired
    private ExpenseService expenseService;

    @Autowired
    private EntityManager em;

    private User ann;
    private User bob;
    private User cid;
    private Group group;

    @BeforeEach
    void createGroupWithThreeMembers() {
        ann = users.save(new User("ann@example.com", "Ann"));
        bob = users.save(new User("bob@example.com", "Bob"));
        cid = users.save(new User("cid@example.com", "Cid"));
        group = groups.save(new Group("Flat", "PLN", ann));
        for (User member : List.of(ann, bob, cid)) {
            memberships.save(new Membership(group, member, MembershipRole.MEMBER));
        }
        em.flush();
        em.clear();
    }

    private ExpenseResponse addEqualExpense(User payer, long amount, User... participants) {
        List<Participant> list = new ArrayList<>();
        for (User participant : participants) {
            list.add(new Participant(participant.getId(), null, null));
        }
        ExpenseResponse response = expenseService.create(group.getId(),
                new CreateExpenseRequest("expense", amount, "PLN", payer.getId(), SplitType.EQUAL, null, list));
        em.flush();
        em.clear();
        return response;
    }

    private static String balanceOf(UUID user) {
        return "$.balances[?(@.userId == '%s')].balance".formatted(user);
    }

    @Test
    void newGroupHasZeroBalancesForEveryMember() throws Exception {
        mvc.perform(get("/groups/{id}/balances", group.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("PLN"))
                .andExpect(jsonPath("$.balances.length()").value(3))
                .andExpect(jsonPath(balanceOf(ann.getId())).value(contains(0)))
                .andExpect(jsonPath(balanceOf(bob.getId())).value(contains(0)))
                .andExpect(jsonPath(balanceOf(cid.getId())).value(contains(0)));
    }

    @Test
    void paidMinusOwedGivesTheNetBalance() throws Exception {
        addEqualExpense(ann, 3000, ann, bob, cid);
        addEqualExpense(bob, 1000, ann, bob);

        mvc.perform(get("/groups/{id}/balances", group.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath(balanceOf(ann.getId())).value(contains(1500)))
                .andExpect(jsonPath(balanceOf(bob.getId())).value(contains(-500)))
                .andExpect(jsonPath(balanceOf(cid.getId())).value(contains(-1000)));
    }

    @Test
    void listsCreditorsBeforeDebtors() throws Exception {
        addEqualExpense(ann, 3000, ann, bob, cid);
        addEqualExpense(bob, 1000, ann, bob);

        mvc.perform(get("/groups/{id}/balances", group.getId()))
                .andExpect(jsonPath("$.balances[0].displayName").value("Ann"))
                .andExpect(jsonPath("$.balances[1].displayName").value("Bob"))
                .andExpect(jsonPath("$.balances[2].displayName").value("Cid"));
    }

    @Test
    void payerWhoOnlyPaysForOthersIsOwedTheFullAmount() throws Exception {
        addEqualExpense(ann, 1000, bob, cid);

        mvc.perform(get("/groups/{id}/balances", group.getId()))
                .andExpect(jsonPath(balanceOf(ann.getId())).value(contains(1000)))
                .andExpect(jsonPath(balanceOf(bob.getId())).value(contains(-500)))
                .andExpect(jsonPath(balanceOf(cid.getId())).value(contains(-500)));
    }

    @Test
    void unknownGroupIsNotFound() throws Exception {
        mvc.perform(get("/groups/{id}/balances", UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void balancesAlwaysSumToZeroAndMatchWhatEachPersonPaidMinusOwed() throws Exception {
        Random random = new Random(11);
        List<User> everyone = List.of(ann, bob, cid);
        Map<UUID, Long> expected = new HashMap<>();
        everyone.forEach(u -> expected.put(u.getId(), 0L));

        for (int i = 0; i < 40; i++) {
            long amount = 1 + random.nextInt(100_000);
            User payer = everyone.get(random.nextInt(everyone.size()));
            List<User> shuffled = new ArrayList<>(everyone);
            Collections.shuffle(shuffled, random);
            User[] participants = shuffled.subList(0, 1 + random.nextInt(3)).toArray(new User[0]);

            ExpenseResponse response = addEqualExpense(payer, amount, participants);

            expected.merge(payer.getId(), amount, Long::sum);
            response.splits().forEach(s -> expected.merge(s.userId(), -s.amount(), Long::sum));
        }

        assertThatSumIsZero(expected);
        var result = mvc.perform(get("/groups/{id}/balances", group.getId())).andExpect(status().isOk());
        for (User user : everyone) {
            result.andExpect(jsonPath(balanceOf(user.getId())).value(contains(Math.toIntExact(expected.get(user.getId())))));
        }
    }

    private static void assertThatSumIsZero(Map<UUID, Long> balances) {
        long sum = balances.values().stream().mapToLong(Long::longValue).sum();
        org.assertj.core.api.Assertions.assertThat(sum).isZero();
    }
}
