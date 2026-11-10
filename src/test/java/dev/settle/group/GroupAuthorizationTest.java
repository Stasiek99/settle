package dev.settle.group;

import dev.settle.TestcontainersConfiguration;
import dev.settle.expense.CreateExpenseRequest;
import dev.settle.expense.CreateExpenseRequest.Participant;
import dev.settle.expense.ExpenseRepository;
import dev.settle.expense.ExpenseService;
import dev.settle.expense.SplitType;
import dev.settle.settlement.CreateSettlementRequest;
import dev.settle.settlement.SettlementRepository;
import dev.settle.settlement.SettlementService;
import dev.settle.support.TestAuth;
import dev.settle.user.User;
import dev.settle.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class GroupAuthorizationTest {

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
    private SettlementRepository settlements;

    @Autowired
    private ExpenseService expenseService;

    @Autowired
    private SettlementService settlementService;

    @Autowired
    private EntityManager em;

    private User ann;
    private User bob;
    private User cid;
    private Group group;
    private UUID expenseId;
    private UUID settlementId;

    @BeforeEach
    void createGroupWithAnOwnerAMemberAndAnOutsider() {
        ann = users.save(new User("ann@example.com", "Ann"));
        bob = users.save(new User("bob@example.com", "Bob"));
        cid = users.save(new User("cid@example.com", "Cid"));
        group = groups.save(new Group("Flat", "PLN", ann));
        memberships.save(new Membership(group, ann, MembershipRole.OWNER));
        memberships.save(new Membership(group, bob, MembershipRole.MEMBER));

        expenseId = expenseService.create(group.getId(), new CreateExpenseRequest(
                "Pizza", 1000L, "PLN", ann.getId(), SplitType.EQUAL, null,
                List.of(new Participant(ann.getId(), null, null), new Participant(bob.getId(), null, null)))).id();
        settlementId = settlementService.create(group.getId(),
                new CreateSettlementRequest(bob.getId(), ann.getId(), 100L, "PLN", null)).id();
        em.flush();
        em.clear();
    }

    private ResultActions call(String method, String template) throws Exception {
        String path = template
                .replace("{g}", group.getId().toString())
                .replace("{e}", expenseId.toString())
                .replace("{s}", settlementId.toString());
        return mvc.perform(request(HttpMethod.valueOf(method), path)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "GET,/groups/{g}",
            "GET,/groups/{g}/members",
            "POST,/groups/{g}/members",
            "GET,/groups/{g}/expenses",
            "POST,/groups/{g}/expenses",
            "GET,/groups/{g}/expenses/{e}",
            "GET,/groups/{g}/balances",
            "GET,/groups/{g}/settlements",
            "POST,/groups/{g}/settlements",
            "GET,/groups/{g}/settlements/{s}",
            "GET,/groups/{g}/settlements/suggestions"
    })
    void anOutsiderSeesNothingOfTheGroupAndCannotTellItExists(String method, String path) throws Exception {
        TestAuth.authenticateAs(cid.getId());

        call(method, path)
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value(containsString("not found")));
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "GET,/groups/{g}",
            "GET,/groups/{g}/members",
            "GET,/groups/{g}/expenses",
            "GET,/groups/{g}/expenses/{e}",
            "GET,/groups/{g}/balances",
            "GET,/groups/{g}/settlements",
            "GET,/groups/{g}/settlements/{s}",
            "GET,/groups/{g}/settlements/suggestions"
    })
    void aPlainMemberCanReadEverythingInTheGroup(String method, String path) throws Exception {
        TestAuth.authenticateAs(bob.getId());

        call(method, path).andExpect(status().isOk());
    }

    @Test
    void anOutsiderIsTurnedAwayBeforeTheRequestBodyIsEvenValidated() throws Exception {
        TestAuth.authenticateAs(cid.getId());

        mvc.perform(post("/groups/{g}/expenses", group.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not even json"))
                .andExpect(status().isNotFound());
    }

    @Test
    void anOutsiderCannotWriteAnythingEvenWithAWellFormedRequest() throws Exception {
        TestAuth.authenticateAs(cid.getId());
        long expensesBefore = expenses.count();
        long settlementsBefore = settlements.count();
        long membershipsBefore = memberships.count();

        mvc.perform(post("/groups/{g}/expenses", group.getId()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"description":"Sneaky","amount":500,"currency":"PLN","paidBy":"%s","splitType":"EQUAL",
                         "participants":[{"userId":"%s"},{"userId":"%s"}]}
                        """.formatted(ann.getId(), ann.getId(), bob.getId())))
                .andExpect(status().isNotFound());
        mvc.perform(post("/groups/{g}/settlements", group.getId()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"paidBy":"%s","paidTo":"%s","amount":100,"currency":"PLN"}
                        """.formatted(bob.getId(), ann.getId())))
                .andExpect(status().isNotFound());
        mvc.perform(post("/groups/{g}/members", group.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": \"%s\"}".formatted(cid.getId())))
                .andExpect(status().isNotFound());

        assertThat(expenses.count()).isEqualTo(expensesBefore);
        assertThat(settlements.count()).isEqualTo(settlementsBefore);
        assertThat(memberships.count()).isEqualTo(membershipsBefore);
    }

    @Test
    void onlyTheOwnerCanAddMembers() throws Exception {
        String addCid = "{\"userId\": \"%s\"}".formatted(cid.getId());

        TestAuth.authenticateAs(bob.getId());
        mvc.perform(post("/groups/{g}/members", group.getId()).contentType(MediaType.APPLICATION_JSON).content(addCid))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value("Only the group owner can do this"));
        assertThat(memberships.existsByGroupIdAndUserId(group.getId(), cid.getId())).isFalse();

        TestAuth.authenticateAs(ann.getId());
        mvc.perform(post("/groups/{g}/members", group.getId()).contentType(MediaType.APPLICATION_JSON).content(addCid))
                .andExpect(status().isCreated());
        assertThat(memberships.existsByGroupIdAndUserId(group.getId(), cid.getId())).isTrue();
    }

    @Test
    void aMemberCannotPromoteThemselvesByAddingThemselvesAgain() throws Exception {
        TestAuth.authenticateAs(bob.getId());

        mvc.perform(post("/groups/{g}/members", group.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": \"%s\"}".formatted(bob.getId())))
                .andExpect(status().isForbidden());
        assertThat(memberships.existsByGroupIdAndUserIdAndRole(group.getId(), bob.getId(), MembershipRole.OWNER))
                .isFalse();
    }

    @Test
    void theCreatorOfAGroupIsAlwaysTheCallerNeverWhoeverTheBodyNames() throws Exception {
        TestAuth.authenticateAs(ann.getId());

        String json = mvc.perform(post("/groups").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"Mine","currency":"PLN","createdBy":"%s"}
                        """.formatted(bob.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdBy").value(ann.getId().toString()))
                .andReturn().getResponse().getContentAsString();
        UUID newGroup = UUID.fromString(json.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));

        assertThat(memberships.existsByGroupIdAndUserIdAndRole(newGroup, ann.getId(), MembershipRole.OWNER)).isTrue();
        assertThat(memberships.existsByGroupIdAndUserId(newGroup, bob.getId())).isFalse();
    }

    @Test
    void membershipOfAnotherGroupGivesNoAccessToThisOne() throws Exception {
        Group cidsGroup = groups.save(new Group("Cids", "PLN", cid));
        memberships.save(new Membership(cidsGroup, cid, MembershipRole.OWNER));
        em.flush();
        em.clear();
        TestAuth.authenticateAs(cid.getId());

        mvc.perform(get("/groups/{g}", cidsGroup.getId())).andExpect(status().isOk());
        mvc.perform(get("/groups/{g}", group.getId())).andExpect(status().isNotFound());
        mvc.perform(get("/groups/{g}/expenses/{e}", cidsGroup.getId(), expenseId)).andExpect(status().isNotFound());
    }

    @Test
    void theListOfGroupsContainsOnlyGroupsTheCallerBelongsTo() throws Exception {
        TestAuth.authenticateAs(cid.getId());

        mvc.perform(get("/groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void anUnknownAndAForbiddenGroupLookLikeTheSameThing() throws Exception {
        TestAuth.authenticateAs(cid.getId());

        String forbidden = mvc.perform(get("/groups/{g}", group.getId()))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String unknown = mvc.perform(get("/groups/{g}", UUID.randomUUID()))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();

        assertThat(forbidden.replaceAll("[0-9a-f-]{36}", "<id>")).isEqualTo(unknown.replaceAll("[0-9a-f-]{36}", "<id>"));
    }
}
