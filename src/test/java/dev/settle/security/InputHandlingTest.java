package dev.settle.security;

import dev.settle.TestcontainersConfiguration;
import dev.settle.auth.JwtService;
import dev.settle.expense.ExpenseRepository;
import dev.settle.group.Group;
import dev.settle.group.GroupRepository;
import dev.settle.group.Membership;
import dev.settle.group.MembershipRepository;
import dev.settle.group.MembershipRole;
import dev.settle.user.User;
import dev.settle.user.UserRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class InputHandlingTest {

    private static final String PASSWORD = "correct-horse";

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
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User ann;
    private User bob;
    private Group group;
    private String bearer;

    @BeforeEach
    void createGroup() {
        ann = users.save(new User("ann@example.com", "Ann", passwordEncoder.encode(PASSWORD)));
        bob = users.save(new User("bob@example.com", "Bob", passwordEncoder.encode(PASSWORD)));
        group = groups.save(new Group("Flat", "PLN", ann));
        memberships.save(new Membership(group, ann, MembershipRole.OWNER));
        memberships.save(new Membership(group, bob, MembershipRole.MEMBER));
        bearer = "Bearer " + jwtService.issueFor(ann).accessToken();
    }

    private ResultActions postJson(String path, String json) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json)
                .header(HttpHeaders.AUTHORIZATION, bearer));
    }

    private ResultActions register(String json) throws Exception {
        return mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    private ResultActions addExpense(String description, String amountLiteral) throws Exception {
        return postJson("/groups/" + group.getId() + "/expenses", """
                {"description":"%s","amount":%s,"currency":"PLN","paidBy":"%s","splitType":"EQUAL",
                 "participants":[{"userId":"%s"},{"userId":"%s"}]}
                """.formatted(description, amountLiteral, ann.getId(), ann.getId(), bob.getId()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"' OR '1'='1' --", "admin'--", "\\\" OR \\\"\\\"=\\\"", "'; DROP TABLE users; --",
            "ann@example.com' OR 1=1 --"})
    void sqlInjectionInTheLoginFormIsJustAWrongLogin(String payload) throws Exception {
        login(payload, "anything-at-all").andExpect(status().isUnauthorized());
        login("ann@example.com", payload).andExpect(status().isUnauthorized());
    }

    @Test
    void sqlMetacharactersInTextAreStoredLiterallyAndNothingIsExecuted() throws Exception {
        String name = "Robert'); DROP TABLE users;--";

        register("{\"email\":\"robert@example.com\",\"displayName\":\"%s\",\"password\":\"%s\"}"
                .formatted(name, PASSWORD)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.displayName").value(name));

        assertThat(users.findByEmail("robert@example.com")).isPresent();
        assertThat(users.count()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void sqlMetacharactersInAnExpenseDescriptionAreStoredLiterally() throws Exception {
        String description = "'; DELETE FROM expenses; --";

        addExpense(description, "1000").andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value(description));

        assertThat(expenses.count()).isEqualTo(1);
    }

    @Test
    void nullCharactersInNamesAreRejectedWithA400NotAServerError() throws Exception {
        String nul = "\\u0000";

        register("{\"email\":\"nul@example.com\",\"displayName\":\"Ann%s\",\"password\":\"%s\"}"
                .formatted(nul, PASSWORD)).andExpect(status().isBadRequest());
        postJson("/groups", "{\"name\":\"Flat%s\",\"currency\":\"PLN\"}".formatted(nul))
                .andExpect(status().isBadRequest());
        addExpense("Pizza" + nul, "1000").andExpect(status().isBadRequest());
    }

    @Test
    void controlCharactersInTextAreRejectedToo() throws Exception {
        for (String control : new String[]{"\\u0007", "\\u001b", "\\r", "\\n", "\\t"}) {
            addExpense("Pizza" + control + "party", "1000").andExpect(status().isBadRequest());
        }
    }

    @Test
    void aPasswordWithANullCharacterIsRejectedNotMishandled() throws Exception {
        register("{\"email\":\"nul@example.com\",\"displayName\":\"Nul\",\"password\":\"abc\\u0000defgh\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void ordinaryUnicodeSurvivesARoundTrip() throws Exception {
        String name = "Zażółć gęślą jaźń 😀 日本語";

        addExpense(name, "1000").andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value(name));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1000.9", "1000.5", "0.5", "1e3", "1E2"})
    void moneyIsNeverSilentlyTruncatedOrReinterpretedFromAFraction(String amount) throws Exception {
        long before = expenses.count();

        addExpense("Pizza", amount).andExpect(status().isBadRequest());

        assertThat(expenses.count()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"99999999999999999999", "1e30", "-1", "0", "\\\"abc\\\"", "null", "true", "[]", "{}"})
    void nonsenseAmountsAreRejected(String amount) throws Exception {
        addExpense("Pizza", amount.replace("\\\"", "\"")).andExpect(status().isBadRequest());
    }

    @Test
    void aFractionalPercentageIsRejectedInsteadOfBeingTruncated() throws Exception {
        postJson("/groups/" + group.getId() + "/expenses", """
                {"description":"Taxi","amount":1000,"currency":"PLN","paidBy":"%s","splitType":"PERCENTAGE",
                 "participants":[{"userId":"%s","basisPoints":5000.7},{"userId":"%s","basisPoints":5000}]}
                """.formatted(ann.getId(), ann.getId(), bob.getId())).andExpect(status().isBadRequest());
    }

    @Test
    void extremelyDeepJsonIsRejectedWithoutBlowingTheStack() throws Exception {
        String deep = "[".repeat(100_000);

        postJson("/groups", deep).andExpect(status().is4xxClientError());
    }

    @Test
    void aHugeButValidlyShapedTextFieldIsRejectedByTheLengthLimit() throws Exception {
        postJson("/groups", "{\"name\":\"%s\",\"currency\":\"PLN\"}".formatted("x".repeat(2_000_000)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void extraFieldsInARegistrationCannotChooseTheIdTheHashOrTheCreationDate() throws Exception {
        String chosenId = "11111111-1111-1111-1111-111111111111";

        register("""
                {"email":"mallory@example.com","displayName":"Mallory","password":"%s",
                 "id":"%s","passwordHash":"{noop}letmein","createdAt":"1999-01-01T00:00:00Z","role":"ADMIN"}
                """.formatted(PASSWORD, chosenId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not(chosenId)))
                .andExpect(jsonPath("$.createdAt").value(org.hamcrest.Matchers.not("1999-01-01T00:00:00Z")));

        String hash = users.findByEmail("mallory@example.com").orElseThrow().getPasswordHash();
        assertThat(hash).startsWith("{bcrypt}");
        assertThat(passwordEncoder.matches(PASSWORD, hash)).isTrue();
        assertThat(passwordEncoder.matches("letmein", hash)).isFalse();
        login("mallory@example.com", "letmein").andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"../../etc/passwd", "..%2F..%2Fetc%2Fpasswd", "%00", "1; DROP TABLE groups", "<script>alert(1)</script>"})
    void hostilePathSegmentsNeverReachTheDatabase(String segment) throws Exception {
        mvc.perform(get("/groups/{id}", segment).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void scriptsInTextComeBackAsDataWithAJsonContentTypeAndNosniff() throws Exception {
        String payload = "<script>alert(1)</script>";

        addExpense(payload, "1000").andExpect(status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("X-Content-Type-Options", "nosniff"))
                .andExpect(jsonPath("$.description").value(payload));
    }
}
