package dev.settle.settlement;

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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class SettlementApiTest {

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

    private ResultActions postSettlement(UUID groupId, UUID paidBy, UUID paidTo, long amount) throws Exception {
        ResultActions result = mvc.perform(post("/groups/{id}/settlements", groupId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"paidBy":"%s","paidTo":"%s","amount":%d,"currency":"PLN"}
                        """.formatted(paidBy, paidTo, amount)));
        em.flush();
        em.clear();
        return result;
    }

    private static String balanceOf(UUID user) {
        return "$.balances[?(@.userId == '%s')].balance".formatted(user);
    }

    @Test
    void createsASettlementAndReturnsItWithALocation() throws Exception {
        String location = postSettlement(group.getId(), bob.getId(), ann.getId(), 1000)
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.paidBy").value(bob.getId().toString()))
                .andExpect(jsonPath("$.paidTo").value(ann.getId().toString()))
                .andExpect(jsonPath("$.amount").value(1000))
                .andExpect(jsonPath("$.currency").value("PLN"))
                .andExpect(jsonPath("$.settledOn").isNotEmpty())
                .andReturn().getResponse().getHeader("Location");

        mvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(1000));
    }

    @Test
    void aSettlementMovesTheBalancesTowardsZero() throws Exception {
        addEqualExpense(ann, 3000, ann, bob, cid);

        postSettlement(group.getId(), bob.getId(), ann.getId(), 1000).andExpect(status().isCreated());

        mvc.perform(get("/groups/{id}/balances", group.getId()))
                .andExpect(jsonPath(balanceOf(ann.getId())).value(contains(1000)))
                .andExpect(jsonPath(balanceOf(bob.getId())).value(contains(0)))
                .andExpect(jsonPath(balanceOf(cid.getId())).value(contains(-1000)));
    }

    @Test
    void aPartialSettlementLeavesTheRestOfTheDebt() throws Exception {
        addEqualExpense(ann, 3000, ann, bob, cid);

        postSettlement(group.getId(), bob.getId(), ann.getId(), 400);

        mvc.perform(get("/groups/{id}/balances", group.getId()))
                .andExpect(jsonPath(balanceOf(ann.getId())).value(contains(1600)))
                .andExpect(jsonPath(balanceOf(bob.getId())).value(contains(-600)));
    }

    @Test
    void payingMoreThanOwedTurnsTheDebtAround() throws Exception {
        addEqualExpense(ann, 3000, ann, bob, cid);

        postSettlement(group.getId(), bob.getId(), ann.getId(), 1500);

        mvc.perform(get("/groups/{id}/balances", group.getId()))
                .andExpect(jsonPath(balanceOf(ann.getId())).value(contains(500)))
                .andExpect(jsonPath(balanceOf(bob.getId())).value(contains(500)));
    }

    @Test
    void listsSettlementsNewestFirst() throws Exception {
        postSettlement(group.getId(), bob.getId(), ann.getId(), 100);
        postSettlement(group.getId(), cid.getId(), ann.getId(), 200);

        mvc.perform(get("/groups/{id}/settlements", group.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].amount").value(200))
                .andExpect(jsonPath("$[1].amount").value(100));
    }

    @Test
    void rejectsPayingYourself() throws Exception {
        postSettlement(group.getId(), ann.getId(), ann.getId(), 100)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("different people")));
    }

    @Test
    void rejectsAPersonWhoIsNotInTheGroup() throws Exception {
        User stranger = users.save(new User("stranger@example.com", "Stranger"));

        postSettlement(group.getId(), stranger.getId(), ann.getId(), 100)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("not a member")));
    }

    @Test
    void rejectsInvalidFieldsWithAPerFieldList() throws Exception {
        mvc.perform(post("/groups/{id}/settlements", group.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":-5,\"currency\":\"zloty\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'paidBy')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'paidTo')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'amount')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'currency')]").exists());
    }

    @Test
    void unknownGroupIsNotFound() throws Exception {
        postSettlement(UUID.randomUUID(), bob.getId(), ann.getId(), 100).andExpect(status().isNotFound());
        mvc.perform(get("/groups/{id}/settlements", UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void aSettlementIsNotVisibleThroughAnotherGroup() throws Exception {
        Group other = groups.save(new Group("Other", "PLN", ann));
        em.flush();
        em.clear();
        String location = postSettlement(group.getId(), bob.getId(), ann.getId(), 100)
                .andReturn().getResponse().getHeader("Location");
        UUID settlementId = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));

        mvc.perform(get("/groups/{g}/settlements/{s}", other.getId(), settlementId)).andExpect(status().isNotFound());
        mvc.perform(get("/groups/{g}/settlements/{s}", group.getId(), settlementId)).andExpect(status().isOk());
    }

    @Test
    void balancesStayZeroSumAndMatchExpensesPlusSettlements() throws Exception {
        Random random = new Random(23);
        List<User> everyone = List.of(ann, bob, cid);
        Map<UUID, Long> expected = new HashMap<>();
        everyone.forEach(u -> expected.put(u.getId(), 0L));

        for (int i = 0; i < 40; i++) {
            if (random.nextInt(3) == 0) {
                List<User> pair = new ArrayList<>(everyone);
                Collections.shuffle(pair, random);
                long amount = 1 + random.nextInt(50_000);
                postSettlement(group.getId(), pair.get(0).getId(), pair.get(1).getId(), amount)
                        .andExpect(status().isCreated());
                expected.merge(pair.get(0).getId(), amount, Long::sum);
                expected.merge(pair.get(1).getId(), -amount, Long::sum);
                continue;
            }
            long amount = 1 + random.nextInt(100_000);
            User payer = everyone.get(random.nextInt(everyone.size()));
            List<User> shuffled = new ArrayList<>(everyone);
            Collections.shuffle(shuffled, random);
            User[] participants = shuffled.subList(0, 1 + random.nextInt(3)).toArray(new User[0]);
            ExpenseResponse response = addEqualExpense(payer, amount, participants);
            expected.merge(payer.getId(), amount, Long::sum);
            response.splits().forEach(s -> expected.merge(s.userId(), -s.amount(), Long::sum));
        }

        assertThat(expected.values().stream().mapToLong(Long::longValue).sum()).isZero();
        ResultActions result = mvc.perform(get("/groups/{id}/balances", group.getId())).andExpect(status().isOk());
        for (User user : everyone) {
            result.andExpect(jsonPath(balanceOf(user.getId())).value(contains(Math.toIntExact(expected.get(user.getId())))));
        }
    }
}
