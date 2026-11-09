package dev.settle.settlement;

import dev.settle.TestcontainersConfiguration;
import dev.settle.expense.CreateExpenseRequest;
import dev.settle.expense.CreateExpenseRequest.Participant;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class SettlementSuggestionApiTest {

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
    private SettlementService settlementService;

    @Autowired
    private SettlementSuggestionService suggestionService;

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

    private void addEqualExpense(User payer, long amount, User... participants) {
        List<Participant> list = new ArrayList<>();
        for (User participant : participants) {
            list.add(new Participant(participant.getId(), null, null));
        }
        expenseService.create(group.getId(),
                new CreateExpenseRequest("expense", amount, "PLN", payer.getId(), SplitType.EQUAL, null, list));
        em.flush();
        em.clear();
    }

    private static String amountPaidBy(UUID user) {
        return "$.transfers[?(@.paidBy == '%s')].amount".formatted(user);
    }

    @Test
    void groupWithoutDebtsHasNoSuggestions() throws Exception {
        mvc.perform(get("/groups/{id}/settlements/suggestions", group.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("PLN"))
                .andExpect(jsonPath("$.transfers.length()").value(0));
    }

    @Test
    void suggestsThatEachDebtorPaysTheCreditor() throws Exception {
        addEqualExpense(ann, 3000, ann, bob, cid);

        mvc.perform(get("/groups/{id}/settlements/suggestions", group.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transfers.length()").value(2))
                .andExpect(jsonPath(amountPaidBy(bob.getId())).value(contains(1000)))
                .andExpect(jsonPath(amountPaidBy(cid.getId())).value(contains(1000)))
                .andExpect(jsonPath("$.transfers[0].paidTo").value(ann.getId().toString()))
                .andExpect(jsonPath("$.transfers[1].paidTo").value(ann.getId().toString()));
    }

    @Test
    void suggestionsShrinkAfterASettlementIsRecorded() throws Exception {
        addEqualExpense(ann, 3000, ann, bob, cid);
        settlementService.create(group.getId(), new CreateSettlementRequest(bob.getId(), ann.getId(), 1000L, "PLN", null));
        em.flush();
        em.clear();

        mvc.perform(get("/groups/{id}/settlements/suggestions", group.getId()))
                .andExpect(jsonPath("$.transfers.length()").value(1))
                .andExpect(jsonPath("$.transfers[0].paidBy").value(cid.getId().toString()))
                .andExpect(jsonPath("$.transfers[0].paidTo").value(ann.getId().toString()))
                .andExpect(jsonPath("$.transfers[0].amount").value(1000));
    }

    @Test
    void unknownGroupIsNotFound() throws Exception {
        mvc.perform(get("/groups/{id}/settlements/suggestions", UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void recordingEverySuggestionLeavesEveryoneAtZero() throws Exception {
        Random random = new Random(41);
        List<User> everyone = List.of(ann, bob, cid);
        for (int i = 0; i < 25; i++) {
            User payer = everyone.get(random.nextInt(everyone.size()));
            List<User> shuffled = new ArrayList<>(everyone);
            Collections.shuffle(shuffled, random);
            addEqualExpense(payer, 1 + random.nextInt(100_000), shuffled.subList(0, 1 + random.nextInt(3)).toArray(new User[0]));
        }

        SettlementSuggestionsResponse suggestions = suggestionService.forGroup(group.getId());
        assertThat(suggestions.transfers().size()).isLessThanOrEqualTo(2);
        for (SettlementSuggestionsResponse.Transfer transfer : suggestions.transfers()) {
            settlementService.create(group.getId(),
                    new CreateSettlementRequest(transfer.paidBy(), transfer.paidTo(), transfer.amount(), "PLN", null));
        }
        em.flush();
        em.clear();

        ResultActions balances = mvc.perform(get("/groups/{id}/balances", group.getId())).andExpect(status().isOk());
        for (User user : everyone) {
            balances.andExpect(jsonPath("$.balances[?(@.userId == '%s')].balance".formatted(user.getId())).value(contains(0)));
        }
        assertThat(suggestionService.forGroup(group.getId()).transfers()).isEmpty();
    }
}
