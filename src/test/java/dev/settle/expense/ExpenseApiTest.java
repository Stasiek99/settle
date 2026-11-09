package dev.settle.expense;

import dev.settle.TestcontainersConfiguration;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
@WithMockUser
class ExpenseApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private GroupRepository groups;

    @Autowired
    private MembershipRepository memberships;

    @Autowired
    private EntityManager em;

    private List<User> members;
    private Group group;

    @BeforeEach
    void createGroupWithThreeMembers() {
        members = List.of(
                        users.save(new User("ann@example.com", "Ann")),
                        users.save(new User("bob@example.com", "Bob")),
                        users.save(new User("cid@example.com", "Cid")))
                .stream().sorted(Comparator.comparing(User::getId)).toList();
        group = groups.save(new Group("Flat", "PLN", members.getFirst()));
        memberships.save(new Membership(group, members.get(0), MembershipRole.OWNER));
        memberships.save(new Membership(group, members.get(1), MembershipRole.MEMBER));
        memberships.save(new Membership(group, members.get(2), MembershipRole.MEMBER));
        em.flush();
        em.clear();
    }

    private UUID id(int member) {
        return members.get(member).getId();
    }

    private ResultActions send(ResultActions actions) {
        em.flush();
        em.clear();
        return actions;
    }

    private ResultActions postExpense(UUID groupId, String json) throws Exception {
        return send(mvc.perform(post("/groups/{id}/expenses", groupId)
                .contentType(MediaType.APPLICATION_JSON).content(json)));
    }

    private String equalExpense(String description, long amount, UUID paidBy, UUID... participants) {
        StringBuilder list = new StringBuilder();
        for (UUID p : participants) {
            list.append(list.isEmpty() ? "" : ",").append("{\"userId\":\"%s\"}".formatted(p));
        }
        return """
                {"description":"%s","amount":%d,"currency":"PLN","paidBy":"%s",
                 "splitType":"EQUAL","participants":[%s]}
                """.formatted(description, amount, paidBy, list);
    }

    private static String shareOf(UUID user) {
        return "$.splits[?(@.userId == '%s')].amount".formatted(user);
    }

    @Test
    void theExtraGroszMovesOnlyWhenThereWasOneToHandOut() throws Exception {
        UUID[] all = {id(0), id(1), id(2)};

        postExpense(group.getId(), equalExpense("first", 1000, id(0), all))
                .andExpect(status().isCreated())
                .andExpect(jsonPath(shareOf(id(0))).value(contains(334)))
                .andExpect(jsonPath(shareOf(id(1))).value(contains(333)))
                .andExpect(jsonPath(shareOf(id(2))).value(contains(333)));

        postExpense(group.getId(), equalExpense("second", 900, id(0), all))
                .andExpect(status().isCreated())
                .andExpect(jsonPath(shareOf(id(0))).value(contains(300)))
                .andExpect(jsonPath(shareOf(id(1))).value(contains(300)))
                .andExpect(jsonPath(shareOf(id(2))).value(contains(300)));

        postExpense(group.getId(), equalExpense("third", 1000, id(0), all))
                .andExpect(status().isCreated())
                .andExpect(jsonPath(shareOf(id(0))).value(contains(333)))
                .andExpect(jsonPath(shareOf(id(1))).value(contains(334)))
                .andExpect(jsonPath(shareOf(id(2))).value(contains(333)));

        assertThat(groups.findById(group.getId()).orElseThrow().getRotationCursor()).isEqualTo(2);
    }

    @Test
    void createdExpenseIsReturnedWithLocationAndCanBeFetched() throws Exception {
        String location = postExpense(group.getId(), equalExpense("Pizza", 1000, id(1), id(0), id(1)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.description").value("Pizza"))
                .andExpect(jsonPath("$.amount").value(1000))
                .andExpect(jsonPath("$.currency").value("PLN"))
                .andExpect(jsonPath("$.paidBy").value(id(1).toString()))
                .andExpect(jsonPath("$.groupId").value(group.getId().toString()))
                .andExpect(jsonPath("$.splitType").value("EQUAL"))
                .andExpect(jsonPath("$.expenseDate").isNotEmpty())
                .andReturn().getResponse().getHeader("Location");

        mvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Pizza"))
                .andExpect(jsonPath("$.splits.length()").value(2));
    }

    @Test
    void listsExpensesNewestFirst() throws Exception {
        postExpense(group.getId(), equalExpense("older", 1000, id(0), id(0), id(1))).andExpect(status().isCreated());
        postExpense(group.getId(), equalExpense("newer", 2000, id(0), id(0), id(1))).andExpect(status().isCreated());

        mvc.perform(get("/groups/{id}/expenses", group.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].description").value("newer"))
                .andExpect(jsonPath("$[1].description").value("older"));
    }

    @Test
    void percentageSplitGivesTheLeftoverToTheLargestRoundingLoss() throws Exception {
        String json = """
                {"description":"Taxi","amount":1000,"currency":"PLN","paidBy":"%s","splitType":"PERCENTAGE",
                 "participants":[{"userId":"%s","basisPoints":3333},
                                 {"userId":"%s","basisPoints":3333},
                                 {"userId":"%s","basisPoints":3334}]}
                """.formatted(id(0), id(0), id(1), id(2));

        postExpense(group.getId(), json)
                .andExpect(status().isCreated())
                .andExpect(jsonPath(shareOf(id(0))).value(contains(333)))
                .andExpect(jsonPath(shareOf(id(1))).value(contains(333)))
                .andExpect(jsonPath(shareOf(id(2))).value(contains(334)));
    }

    @Test
    void exactSplitStoresTheGivenAmounts() throws Exception {
        String json = """
                {"description":"Dinner","amount":5000,"currency":"PLN","paidBy":"%s","splitType":"EXACT",
                 "participants":[{"userId":"%s","amount":3000},{"userId":"%s","amount":2000}]}
                """.formatted(id(0), id(0), id(1));

        postExpense(group.getId(), json)
                .andExpect(status().isCreated())
                .andExpect(jsonPath(shareOf(id(0))).value(contains(3000)))
                .andExpect(jsonPath(shareOf(id(1))).value(contains(2000)));
    }

    @Test
    void rejectsAnExpenseWhoseAmountsDoNotAddUp() throws Exception {
        String json = """
                {"description":"Dinner","amount":5000,"currency":"PLN","paidBy":"%s","splitType":"EXACT",
                 "participants":[{"userId":"%s","amount":3000},{"userId":"%s","amount":1000}]}
                """.formatted(id(0), id(0), id(1));

        postExpense(group.getId(), json)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("add up")));
    }

    @Test
    void rejectsAPayerWhoIsNotInTheGroup() throws Exception {
        User stranger = users.save(new User("stranger@example.com", "Stranger"));

        postExpense(group.getId(), equalExpense("X", 1000, stranger.getId(), id(0), id(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("not a member")));
    }

    @Test
    void rejectsACurrencyThatDiffersFromTheGroup() throws Exception {
        String json = equalExpense("X", 1000, id(0), id(0), id(1)).replace("\"PLN\"", "\"EUR\"");

        postExpense(group.getId(), json)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("does not match")));
    }

    @Test
    void rejectsInvalidFieldsWithAPerFieldList() throws Exception {
        String json = """
                {"description":"","amount":-5,"currency":"zloty","splitType":"EQUAL","participants":[]}
                """;

        postExpense(group.getId(), json)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'description')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'amount')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'currency')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'paidBy')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'participants')]").exists());
    }

    @Test
    void rejectsAnUnknownSplitType() throws Exception {
        String json = equalExpense("X", 1000, id(0), id(0), id(1)).replace("EQUAL", "RANDOM");

        postExpense(group.getId(), json).andExpect(status().isBadRequest());
    }

    @Test
    void unknownGroupIsNotFound() throws Exception {
        postExpense(UUID.randomUUID(), equalExpense("X", 1000, id(0), id(0), id(1)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/groups/{id}/expenses", UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void anExpenseIsNotVisibleThroughAnotherGroup() throws Exception {
        Group other = groups.save(new Group("Other", "PLN", members.getFirst()));
        em.flush();
        em.clear();
        String location = postExpense(group.getId(), equalExpense("Pizza", 1000, id(0), id(0), id(1)))
                .andReturn().getResponse().getHeader("Location");
        UUID expenseId = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));

        mvc.perform(get("/groups/{g}/expenses/{e}", other.getId(), expenseId)).andExpect(status().isNotFound());
        mvc.perform(get("/groups/{g}/expenses/{e}", group.getId(), expenseId)).andExpect(status().isOk());
    }
}
