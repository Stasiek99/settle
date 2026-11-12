package dev.settle.expense;

import dev.settle.TestcontainersConfiguration;
import dev.settle.group.Group;
import dev.settle.group.GroupRepository;
import dev.settle.group.Membership;
import dev.settle.group.MembershipRepository;
import dev.settle.group.MembershipRole;
import dev.settle.support.TestAuth;
import dev.settle.user.User;
import dev.settle.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ExpenseModificationApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private GroupRepository groups;

    @Autowired
    private MembershipRepository memberships;

    @Autowired
    private ExpenseRepository expenses;

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

    private void actAs(int member) {
        TestAuth.authenticateAs(id(member));
    }

    private ResultActions send(ResultActions actions) {
        em.flush();
        em.clear();
        return actions;
    }

    private static String equal(String description, long amount, UUID paidBy, UUID... participants) {
        StringBuilder list = new StringBuilder();
        for (UUID p : participants) {
            list.append(list.isEmpty() ? "" : ",").append("{\"userId\":\"%s\"}".formatted(p));
        }
        return """
                {"description":"%s","amount":%d,"currency":"PLN","paidBy":"%s",
                 "splitType":"EQUAL","participants":[%s]}
                """.formatted(description, amount, paidBy, list);
    }

    private UUID create(String json) throws Exception {
        actAs(0);
        String location = send(mvc.perform(post("/groups/{id}/expenses", group.getId())
                .contentType(MediaType.APPLICATION_JSON).content(json)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    private ResultActions update(UUID expenseId, String ifMatch, String json) throws Exception {
        var request = put("/groups/{g}/expenses/{e}", group.getId(), expenseId)
                .contentType(MediaType.APPLICATION_JSON).content(json);
        if (ifMatch != null) {
            request.header(HttpHeaders.IF_MATCH, ifMatch);
        }
        return send(mvc.perform(request));
    }

    private ResultActions remove(UUID expenseId, String ifMatch) throws Exception {
        var request = delete("/groups/{g}/expenses/{e}", group.getId(), expenseId);
        if (ifMatch != null) {
            request.header(HttpHeaders.IF_MATCH, ifMatch);
        }
        return send(mvc.perform(request));
    }

    private ResultActions fetch(UUID expenseId) throws Exception {
        return send(mvc.perform(get("/groups/{g}/expenses/{e}", group.getId(), expenseId)));
    }

    private static String shareOf(UUID user) {
        return "$.splits[?(@.userId == '%s')].amount".formatted(user);
    }

    @Test
    void anExpenseCarriesItsVersionAsETag() throws Exception {
        actAs(0);
        UUID expenseId = create(equal("Pizza", 1000, id(1), id(0), id(1)));

        fetch(expenseId)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void thePayerCanEditTheExpenseAndTheVersionMovesOn() throws Exception {
        UUID expenseId = create(equal("Pizza", 1000, id(1), id(0), id(1)));

        actAs(1);
        update(expenseId, "\"0\"", equal("Pizza and drinks", 1500, id(1), id(0), id(1)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""))
                .andExpect(jsonPath("$.description").value("Pizza and drinks"))
                .andExpect(jsonPath("$.amount").value(1500))
                .andExpect(jsonPath("$.version").value(1));

        fetch(expenseId)
                .andExpect(jsonPath("$.description").value("Pizza and drinks"))
                .andExpect(jsonPath("$.amount").value(1500))
                .andExpect(jsonPath("$.splits.length()").value(2))
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""));
    }

    @Test
    void theGroupOwnerCanEditSomeoneElsesExpense() throws Exception {
        UUID expenseId = create(equal("Pizza", 1000, id(1), id(0), id(1)));

        actAs(0);
        update(expenseId, "\"0\"", equal("Corrected", 1000, id(1), id(0), id(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Corrected"));
    }

    @Test
    void anotherMemberCannotEditAndNothingChanges() throws Exception {
        UUID expenseId = create(equal("Pizza", 1000, id(1), id(0), id(1)));

        actAs(2);
        update(expenseId, "\"0\"", equal("Hijacked", 1, id(1), id(0), id(1)))
                .andExpect(status().isForbidden());

        fetch(expenseId)
                .andExpect(jsonPath("$.description").value("Pizza"))
                .andExpect(jsonPath("$.amount").value(1000))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void aStaleVersionIsRejected() throws Exception {
        UUID expenseId = create(equal("Pizza", 1000, id(1), id(0), id(1)));
        actAs(1);
        update(expenseId, "\"0\"", equal("First", 1000, id(1), id(0), id(1))).andExpect(status().isOk());

        update(expenseId, "\"0\"", equal("Second", 1000, id(1), id(0), id(1)))
                .andExpect(status().isPreconditionFailed());

        fetch(expenseId).andExpect(jsonPath("$.description").value("First"));
    }

    @Test
    void aMissingIfMatchHeaderIsRequired() throws Exception {
        UUID expenseId = create(equal("Pizza", 1000, id(1), id(0), id(1)));

        actAs(1);
        update(expenseId, null, equal("Pizza", 1000, id(1), id(0), id(1)))
                .andExpect(status().isPreconditionRequired());
    }

    @ParameterizedTest(name = "If-Match {0} is a bad request")
    @ValueSource(strings = {"0", "\"abc\"", "W/\"0\"", "*", "\"-1\""})
    void aMalformedIfMatchHeaderIsABadRequest(String value) throws Exception {
        UUID expenseId = create(equal("Pizza", 1000, id(1), id(0), id(1)));

        actAs(1);
        update(expenseId, value, equal("Pizza", 1000, id(1), id(0), id(1)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aStrangerIsToldTheExpenseDoesNotExistBeforeAnythingElse() throws Exception {
        UUID expenseId = create(equal("Pizza", 1000, id(1), id(0), id(1)));
        User outsider = users.save(new User("eve@example.com", "Eve"));
        em.flush();
        em.clear();

        TestAuth.authenticateAs(outsider.getId());
        update(expenseId, "\"0\"", equal("Hijacked", 1, id(1), id(0), id(1))).andExpect(status().isNotFound());
        remove(expenseId, "\"0\"").andExpect(status().isNotFound());
    }

    @Test
    void anUnknownExpenseIsNotFound() throws Exception {
        actAs(0);
        UUID unknown = UUID.randomUUID();

        update(unknown, "\"0\"", equal("x", 100, id(0), id(0))).andExpect(status().isNotFound());
        remove(unknown, "\"0\"").andExpect(status().isNotFound());
    }

    @Test
    void anExpenseOfAnotherGroupCannotBeReachedThroughThisOne() throws Exception {
        UUID expenseId = create(equal("Pizza", 1000, id(1), id(0), id(1)));
        Group other = groups.save(new Group("Other", "PLN", members.getFirst()));
        memberships.save(new Membership(other, members.get(0), MembershipRole.OWNER));
        em.flush();
        em.clear();

        actAs(0);
        send(mvc.perform(put("/groups/{g}/expenses/{e}", other.getId(), expenseId)
                .header(HttpHeaders.IF_MATCH, "\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(equal("x", 100, id(0), id(0)))))
                .andExpect(status().isNotFound());
        send(mvc.perform(delete("/groups/{g}/expenses/{e}", other.getId(), expenseId)
                .header(HttpHeaders.IF_MATCH, "\"0\"")))
                .andExpect(status().isNotFound());
    }

    @Test
    void theSameValidationAsOnCreateApplies() throws Exception {
        UUID expenseId = create(equal("Pizza", 1000, id(1), id(0), id(1)));
        User outsider = users.save(new User("eve@example.com", "Eve"));
        em.flush();
        em.clear();
        actAs(1);

        update(expenseId, "\"0\"", equal("", 1000, id(1), id(0), id(1))).andExpect(status().isBadRequest());
        update(expenseId, "\"0\"", equal("Pizza", 0, id(1), id(0), id(1))).andExpect(status().isBadRequest());
        update(expenseId, "\"0\"", equal("Pizza", 1000, outsider.getId(), id(0), id(1)))
                .andExpect(status().isBadRequest());
        update(expenseId, "\"0\"", equal("Pizza", 1000, id(1), id(0), outsider.getId()))
                .andExpect(status().isBadRequest());
        update(expenseId, "\"0\"", equal("Pizza", 1000, id(1), id(0), id(0))).andExpect(status().isBadRequest());
        update(expenseId, "\"0\"", """
                {"description":"Pizza","amount":1000,"currency":"EUR","paidBy":"%s",
                 "splitType":"EQUAL","participants":[{"userId":"%s"}]}
                """.formatted(id(1), id(0))).andExpect(status().isBadRequest());

        fetch(expenseId).andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void theCurrencyOfAnExistingExpenseCannotBeChanged() throws Exception {
        UUID expenseId = create(equal("Pizza", 1000, id(1), id(0), id(1)));
        em.flush();
        em.clear();
        actAs(1);

        update(expenseId, "\"0\"", """
                {"description":"Pizza","amount":1000,"currency":"EUR","paidBy":"%s",
                 "splitType":"EQUAL","participants":[{"userId":"%s"}]}
                """.formatted(id(1), id(0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("cannot be changed")));
    }

    @Test
    void editingOnlyTheDescriptionKeepsWhoPaidTheOddGrosz() throws Exception {
        UUID[] all = {id(0), id(1), id(2)};
        UUID first = create(equal("first", 1000, id(0), all));
        create(equal("second", 1000, id(0), all));
        long cursorBefore = groups.findById(group.getId()).orElseThrow().getRotationCursor();

        actAs(0);
        update(first, "\"0\"", equal("first, renamed", 1000, id(0), all))
                .andExpect(status().isOk())
                .andExpect(jsonPath(shareOf(id(0))).value(contains(334)))
                .andExpect(jsonPath(shareOf(id(1))).value(contains(333)))
                .andExpect(jsonPath(shareOf(id(2))).value(contains(333)));

        assertThat(groups.findById(group.getId()).orElseThrow().getRotationCursor()).isEqualTo(cursorBefore);
    }

    @Test
    void takingSomeoneOutAndPuttingSomeoneInRewritesTheSplits() throws Exception {
        UUID expenseId = create(equal("Taxi", 1000, id(0), id(0), id(1)));

        actAs(0);
        update(expenseId, "\"0\"", equal("Taxi", 1000, id(0), id(0), id(2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.splits.length()").value(2))
                .andExpect(jsonPath("$.splits[*].userId").value(containsInAnyOrder(
                        id(0).toString(), id(2).toString())))
                .andExpect(jsonPath("$.splits[*].amount").value(contains(500, 500)));

        assertThat(expenses.findByIdAndGroupId(expenseId, group.getId()).orElseThrow().getSplits())
                .hasSize(2);
    }

    @Test
    void anEqualExpenseCanBecomeAnExactOne() throws Exception {
        UUID expenseId = create(equal("Taxi", 1000, id(0), id(0), id(1)));

        actAs(0);
        update(expenseId, "\"0\"", """
                {"description":"Taxi","amount":1000,"currency":"PLN","paidBy":"%s","splitType":"EXACT",
                 "participants":[{"userId":"%s","amount":700},{"userId":"%s","amount":300}]}
                """.formatted(id(0), id(0), id(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.splitType").value("EXACT"))
                .andExpect(jsonPath(shareOf(id(0))).value(contains(700)))
                .andExpect(jsonPath(shareOf(id(1))).value(contains(300)));
    }

    @Test
    void anEditShowsUpInTheBalances() throws Exception {
        UUID expenseId = create(equal("Taxi", 1000, id(0), id(0), id(1)));
        actAs(0);
        update(expenseId, "\"0\"", equal("Taxi", 3000, id(0), id(0), id(1))).andExpect(status().isOk());

        send(mvc.perform(get("/groups/{g}/balances", group.getId())))
                .andExpect(jsonPath("$.currencies[0].balances[?(@.userId == '%s')].balance".formatted(id(0))).value(contains(1500)))
                .andExpect(jsonPath("$.currencies[0].balances[?(@.userId == '%s')].balance".formatted(id(1))).value(contains(-1500)));
    }

    @Test
    void thePayerCanDeleteTheExpenseAndItLeavesTheListAndTheBalances() throws Exception {
        UUID expenseId = create(equal("Taxi", 1000, id(1), id(0), id(1)));

        actAs(1);
        remove(expenseId, "\"0\"").andExpect(status().isNoContent());

        fetch(expenseId).andExpect(status().isNotFound());
        send(mvc.perform(get("/groups/{g}/expenses", group.getId())))
                .andExpect(jsonPath("$.totalElements").value(0));
        send(mvc.perform(get("/groups/{g}/balances", group.getId())))
                .andExpect(jsonPath("$.currencies[0].balances[*].balance").value(contains(0, 0, 0)));
        assertThat(expenses.findById(expenseId)).isEmpty();
        assertThat(em.createQuery("select count(s) from ExpenseSplit s", Long.class).getSingleResult()).isZero();
    }

    @Test
    void theGroupOwnerCanDeleteSomeoneElsesExpense() throws Exception {
        UUID expenseId = create(equal("Taxi", 1000, id(1), id(0), id(1)));

        actAs(0);
        remove(expenseId, "\"0\"").andExpect(status().isNoContent());
    }

    @Test
    void anotherMemberCannotDelete() throws Exception {
        UUID expenseId = create(equal("Taxi", 1000, id(1), id(0), id(1)));

        actAs(2);
        remove(expenseId, "\"0\"").andExpect(status().isForbidden());

        fetch(expenseId).andExpect(status().isOk());
    }

    @Test
    void deletingNeedsTheCurrentVersion() throws Exception {
        UUID expenseId = create(equal("Taxi", 1000, id(1), id(0), id(1)));
        actAs(1);
        update(expenseId, "\"0\"", equal("Taxi", 1200, id(1), id(0), id(1))).andExpect(status().isOk());

        remove(expenseId, "\"0\"").andExpect(status().isPreconditionFailed());
        remove(expenseId, null).andExpect(status().isPreconditionRequired());
        fetch(expenseId).andExpect(status().isOk());

        remove(expenseId, "\"1\"").andExpect(status().isNoContent());
    }
}
