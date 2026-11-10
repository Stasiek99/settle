package dev.settle.auth;

import dev.settle.TestcontainersConfiguration;
import dev.settle.user.User;
import dev.settle.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AuthApiTest {

    private static final Pattern TOKEN = Pattern.compile("\"accessToken\":\"([^\"]+)\"");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    private ResultActions register(String email, String name, String password) throws Exception {
        return mvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "displayName": "%s", "password": "%s"}
                        """.formatted(email, name, password)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password)));
    }

    private String loginForAccessToken(String email, String password) throws Exception {
        var cookie = login(email, password).andExpect(status().isOk())
                .andReturn().getResponse().getCookie(SessionCookies.ACCESS);
        assertThat(cookie).isNotNull();
        return cookie.getValue();
    }

    @Test
    void registersAUserWithoutReturningAnyCredentials() throws Exception {
        register("Ann@Example.com", "Ann", "correct-horse")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.email").value("ann@example.com"))
                .andExpect(jsonPath("$.displayName").value("Ann"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void storesOnlyASaltedHashOfThePassword() throws Exception {
        register("ann@example.com", "Ann", "correct-horse").andExpect(status().isCreated());

        String hash = users.findByEmail("ann@example.com").orElseThrow().getPasswordHash();

        assertThat(hash).startsWith("{bcrypt}").doesNotContain("correct-horse");
        assertThat(passwordEncoder.matches("correct-horse", hash)).isTrue();
    }

    @Test
    void twoUsersWithTheSamePasswordGetDifferentHashes() throws Exception {
        register("ann@example.com", "Ann", "correct-horse").andExpect(status().isCreated());
        register("bob@example.com", "Bob", "correct-horse").andExpect(status().isCreated());

        assertThat(users.findByEmail("ann@example.com").orElseThrow().getPasswordHash())
                .isNotEqualTo(users.findByEmail("bob@example.com").orElseThrow().getPasswordHash());
    }

    @Test
    void rejectsADuplicateEmailIgnoringCase() throws Exception {
        register("ann@example.com", "Ann", "correct-horse").andExpect(status().isCreated());

        register("ANN@example.com", "Other", "another-password")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A user with this email already exists"));
    }

    @Test
    void rejectsInvalidRegistrationWithAPerFieldList() throws Exception {
        register("not-an-email", "", "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'email')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'displayName')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'password')]").exists());
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void logsInAndHandsTheSessionOverInHttpOnlyCookies() throws Exception {
        User ann = users.save(new User("ann@example.com", "Ann", passwordEncoder.encode("correct-horse")));

        var result = login("ann@example.com", "correct-horse")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ann.getId().toString()))
                .andExpect(jsonPath("$.email").value("ann@example.com"))
                .andReturn().getResponse();

        assertThat(jwtDecoder.decode(result.getCookie(SessionCookies.ACCESS).getValue()).getSubject())
                .isEqualTo(ann.getId().toString());
        assertThat(result.getHeaders(HttpHeaders.SET_COOKIE)).anySatisfy(header -> assertThat(header)
                .startsWith(SessionCookies.ACCESS + "=")
                .contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api", "Max-Age=900"));
        assertThat(result.getHeaders(HttpHeaders.SET_COOKIE)).anySatisfy(header -> assertThat(header)
                .startsWith(SessionCookies.REFRESH + "=")
                .contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/auth", "Max-Age=604800"));
    }

    @Test
    void neverPutsATokenInTheResponseBody() throws Exception {
        users.save(new User("ann@example.com", "Ann", passwordEncoder.encode("correct-horse")));

        String body = login("ann@example.com", "correct-horse").andReturn().getResponse().getContentAsString();
        String accessToken = login("ann@example.com", "correct-horse").andReturn().getResponse()
                .getCookie(SessionCookies.ACCESS).getValue();

        assertThat(body).doesNotContain("accessToken", "refreshToken", "tokenType", accessToken);
    }

    @Test
    void registeredUserCanLogInRightAway() throws Exception {
        register("ann@example.com", "Ann", "correct-horse").andExpect(status().isCreated());

        login("ann@example.com", "correct-horse").andExpect(status().isOk());
    }

    @Test
    void loginIgnoresEmailCaseAndSurroundingSpaces() throws Exception {
        register("ann@example.com", "Ann", "correct-horse").andExpect(status().isCreated());

        login("  ANN@Example.com ", "correct-horse").andExpect(status().isOk());
    }

    @Test
    void wrongPasswordAndUnknownEmailLookExactlyTheSame() throws Exception {
        register("ann@example.com", "Ann", "correct-horse").andExpect(status().isCreated());

        String wrongPassword = login("ann@example.com", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"))
                .andReturn().getResponse().getContentAsString();
        String unknownEmail = login("nobody@example.com", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(unknownEmail).isEqualTo(wrongPassword);
    }

    @Test
    void aUserCreatedWithoutAPasswordCannotLogIn() throws Exception {
        users.save(new User("legacy@example.com", "Legacy"));

        login("legacy@example.com", "anything-at-all").andExpect(status().isUnauthorized());
    }

    @Test
    void loginRequiresBothFields() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'email')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'password')]").exists());
    }

    @Test
    void aValidTokenIsAcceptedAndAForgedOneIsRefused() throws Exception {
        User ann = users.save(new User("ann@example.com", "Ann", passwordEncoder.encode("correct-horse")));
        String token = loginForAccessToken("ann@example.com", "correct-horse");

        mvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ann.getId().toString()))
                .andExpect(jsonPath("$.email").value("ann@example.com"));
        mvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token + "x"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")));
        mvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer garbage"))
                .andExpect(status().isUnauthorized());
    }
}
