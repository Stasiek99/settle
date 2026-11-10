package dev.settle.security;

import dev.settle.TestcontainersConfiguration;
import dev.settle.auth.JwtProperties;
import dev.settle.auth.JwtService;
import dev.settle.expense.CreateExpenseRequest;
import dev.settle.expense.CreateExpenseRequest.Participant;
import dev.settle.expense.ExpenseService;
import dev.settle.expense.SplitType;
import dev.settle.group.Group;
import dev.settle.group.GroupRepository;
import dev.settle.group.Membership;
import dev.settle.group.MembershipRepository;
import dev.settle.group.MembershipRole;
import dev.settle.settlement.CreateSettlementRequest;
import dev.settle.settlement.SettlementService;
import dev.settle.user.User;
import dev.settle.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class SensitiveDataTest {

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.]+");
    private static final String PASSWORD = "Sup3r-S3cret-Pass-9!";

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
    private JwtService jwtService;

    @Autowired
    private JwtProperties jwtProperties;

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
        expenseService.create(group.getId(), new CreateExpenseRequest("Pizza", 1000L, "PLN", ann.getId(),
                SplitType.EQUAL, null,
                List.of(new Participant(ann.getId(), null, null), new Participant(bob.getId(), null, null))));
        settlementService.create(group.getId(),
                new CreateSettlementRequest(bob.getId(), ann.getId(), 100L, "PLN", null));
        bearer = "Bearer " + jwtService.issueFor(bob).accessToken();
    }

    @Test
    void noGroupEndpointRevealsAnEmailAddressOrACredential() throws Exception {
        String g = "/groups/" + group.getId();
        for (String path : List.of("/groups", g, g + "/members", g + "/expenses", g + "/balances",
                g + "/settlements", g + "/settlements/suggestions")) {
            String body = mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

            assertThat(EMAIL.matcher(body).find()).as("email address in %s", path).isFalse();
            assertThat(body).as(path).doesNotContainIgnoringCase("password").doesNotContain("bcrypt");
        }
    }

    @Test
    void theOwnProfileShowsTheOwnEmailButNeverTheHash() throws Exception {
        String body = mvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(body).contains("bob@example.com").doesNotContainIgnoringCase("password").doesNotContain("bcrypt");
    }

    @Test
    void validationErrorsDoNotEchoWhatTheClientSent() throws Exception {
        String body = mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"definitely-not-an-email\",\"displayName\":\"x\",\"password\":\"tiny\"}"))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("definitely-not-an-email").doesNotContain("tiny");
    }

    @Test
    void loginFailuresDoNotEchoTheSubmittedPassword() throws Exception {
        String body = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ann@example.com\",\"password\":\"%s-wrong\"}".formatted(PASSWORD)))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(PASSWORD);
    }

    @Test
    void passwordsTokensAndKeysNeverReachTheLogs(CapturedOutput output) throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"cid@example.com\",\"displayName\":\"Cid\",\"password\":\"%s\"}"
                                .formatted(PASSWORD)))
                .andExpect(status().isCreated());
        String loginBody = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"cid@example.com\",\"password\":\"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"cid@example.com\",\"password\":\"%s-wrong\"}".formatted(PASSWORD)))
                .andExpect(status().isUnauthorized());
        String token = loginBody.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");

        assertThat(output.getAll())
                .doesNotContain(PASSWORD)
                .doesNotContain(token)
                .doesNotContain(jwtProperties.secret())
                .doesNotContain("$2a$")
                .doesNotContain("{bcrypt}");
    }
}
