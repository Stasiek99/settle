package dev.settle.auth;

import dev.settle.TestcontainersConfiguration;
import dev.settle.user.User;
import dev.settle.user.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * Talks to the API the way a browser does: cookies instead of headers, and no automatic CSRF token
 * (the shared MockMvc adds one to every request, which would hide exactly what is tested here).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class SessionApiTest {

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mvc;
    private User ann;

    @BeforeEach
    void setUp() {
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
        ann = users.save(new User("ann@example.com", "Ann", passwordEncoder.encode(PASSWORD)));
    }

    private static Cookie cookieOf(MockHttpServletResponse response, String name) {
        return response.getCookie(name);
    }

    private static String setCookieFor(MockHttpServletResponse response, String name) {
        return response.getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(header -> header.startsWith(name + "=")).findFirst().orElseThrow();
    }

    private String csrfToken() throws Exception {
        return cookieOf(mvc.perform(get("/auth/csrf")).andExpect(status().isNoContent()).andReturn().getResponse(),
                "XSRF-TOKEN").getValue();
    }

    private static MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request, String token) {
        return request.cookie(new Cookie("XSRF-TOKEN", token)).header("X-XSRF-TOKEN", token);
    }

    private MockHttpServletResponse login() throws Exception {
        String token = csrfToken();
        return mvc.perform(withCsrf(post("/auth/login"), token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ann@example.com\",\"password\":\"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse();
    }

    private ResultActions refresh(String refreshToken, String csrf) throws Exception {
        MockHttpServletRequestBuilder request = post("/auth/refresh");
        if (refreshToken != null) {
            request.cookie(new Cookie(SessionCookies.REFRESH, refreshToken));
        }
        return mvc.perform(csrf == null ? request : withCsrf(request, csrf));
    }

    @Test
    void theCsrfCookieIsSecureAndReadableByTheApp() throws Exception {
        MockHttpServletResponse response = mvc.perform(get("/auth/csrf")).andReturn().getResponse();

        String header = setCookieFor(response, "XSRF-TOKEN");
        assertThat(header).contains("Secure", "Path=/").doesNotContain("HttpOnly");
    }

    @Test
    void everyResponseCarriesTheCsrfCookieEvenWhenItIsAnError() throws Exception {
        MockHttpServletResponse response = mvc.perform(get("/users/me")).andExpect(status().isUnauthorized())
                .andReturn().getResponse();

        assertThat(cookieOf(response, "XSRF-TOKEN")).isNotNull();
    }

    @Test
    void loggingInWithoutACsrfTokenIsForbidden() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ann@example.com\",\"password\":\"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("The CSRF token is missing or invalid"));
    }

    @Test
    void registeringWithoutACsrfTokenIsForbidden() throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new@example.com\",\"displayName\":\"New\",\"password\":\"long-enough-password\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aSessionCookieAloneAuthenticatesReads() throws Exception {
        MockHttpServletResponse login = login();

        mvc.perform(get("/users/me").cookie(cookieOf(login, SessionCookies.ACCESS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ann.getId().toString()));
    }

    @Test
    void aCookieAuthenticatedWriteNeedsTheCsrfToken() throws Exception {
        Cookie access = cookieOf(login(), SessionCookies.ACCESS);
        String csrf = csrfToken();
        String body = "{\"name\":\"Trip\",\"currency\":\"PLN\"}";

        mvc.perform(post("/groups").cookie(access).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(withCsrf(post("/groups").cookie(access), csrf).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void aForgedCsrfTokenIsNotEnough() throws Exception {
        Cookie access = cookieOf(login(), SessionCookies.ACCESS);
        String csrf = csrfToken();
        String body = "{\"name\":\"Trip\",\"currency\":\"PLN\"}";

        mvc.perform(post("/groups").cookie(access).cookie(new Cookie("XSRF-TOKEN", csrf))
                        .header("X-XSRF-TOKEN", "another-token").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/groups").cookie(access).header("X-XSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/groups").cookie(access).cookie(new Cookie("XSRF-TOKEN", csrf))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void aRequestWithAnAuthorizationHeaderCannotBeForgedByABrowserSoItNeedsNoCsrfToken() throws Exception {
        String token = cookieOf(login(), SessionCookies.ACCESS).getValue();

        mvc.perform(post("/groups").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Trip\",\"currency\":\"PLN\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void theAuthorizationHeaderWinsOverTheCookie() throws Exception {
        Cookie access = cookieOf(login(), SessionCookies.ACCESS);

        mvc.perform(get("/users/me").cookie(access).header(HttpHeaders.AUTHORIZATION, "Bearer garbage"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aGarbageSessionCookieIsRefused() throws Exception {
        mvc.perform(get("/users/me").cookie(new Cookie(SessionCookies.ACCESS, "garbage")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theSessionCookieIsIgnoredOnTheAuthEndpoints() throws Exception {
        Cookie access = cookieOf(login(), SessionCookies.ACCESS);

        mvc.perform(get("/auth/csrf").cookie(access)).andExpect(status().isNoContent());
    }

    @Test
    void refreshingWithoutACsrfTokenIsForbidden() throws Exception {
        String refreshToken = cookieOf(login(), SessionCookies.REFRESH).getValue();

        refresh(refreshToken, null).andExpect(status().isForbidden());
    }

    @Test
    void refreshingRotatesBothTokensAndTheNewAccessTokenWorks() throws Exception {
        MockHttpServletResponse login = login();
        String oldAccess = cookieOf(login, SessionCookies.ACCESS).getValue();
        String oldRefresh = cookieOf(login, SessionCookies.REFRESH).getValue();

        MockHttpServletResponse refreshed = refresh(oldRefresh, csrfToken())
                .andExpect(status().isNoContent())
                .andReturn().getResponse();

        String newRefresh = cookieOf(refreshed, SessionCookies.REFRESH).getValue();
        assertThat(newRefresh).isNotEqualTo(oldRefresh);
        assertThat(refreshed.getContentAsString()).isEmpty();
        assertThat(setCookieFor(refreshed, SessionCookies.REFRESH))
                .contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/auth");
        mvc.perform(get("/users/me").cookie(cookieOf(refreshed, SessionCookies.ACCESS)))
                .andExpect(status().isOk());
        refresh(newRefresh, csrfToken()).andExpect(status().isNoContent());
        assertThat(oldAccess).isNotBlank();
    }

    @Test
    void anOldRefreshTokenIsRejectedAndTheClientIsToldToDropItsCookies() throws Exception {
        String oldRefresh = cookieOf(login(), SessionCookies.REFRESH).getValue();
        refresh(oldRefresh, csrfToken()).andExpect(status().isNoContent());

        MockHttpServletResponse rejected = refresh(oldRefresh, csrfToken())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("The session has ended. Please sign in again."))
                .andReturn().getResponse();

        assertThat(setCookieFor(rejected, SessionCookies.ACCESS)).contains("Max-Age=0");
        assertThat(setCookieFor(rejected, SessionCookies.REFRESH)).contains("Max-Age=0", "Path=/api/auth");
    }

    @Test
    void refreshingWithoutAnyCookieIsRejected() throws Exception {
        refresh(null, csrfToken())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("The session has ended. Please sign in again."));
    }

    @Test
    void aGarbageRefreshCookieIsRejected() throws Exception {
        refresh("garbage", csrfToken()).andExpect(status().isUnauthorized());
        refresh("a".repeat(43), csrfToken()).andExpect(status().isUnauthorized());
    }

    @Test
    void loggingOutEndsTheSessionAndClearsTheCookies() throws Exception {
        String refreshToken = cookieOf(login(), SessionCookies.REFRESH).getValue();
        String csrf = csrfToken();

        MockHttpServletResponse response = mvc.perform(withCsrf(post("/auth/logout"), csrf)
                        .cookie(new Cookie(SessionCookies.REFRESH, refreshToken)))
                .andExpect(status().isNoContent())
                .andReturn().getResponse();

        assertThat(setCookieFor(response, SessionCookies.ACCESS)).contains("Max-Age=0");
        assertThat(setCookieFor(response, SessionCookies.REFRESH)).contains("Max-Age=0");
        refresh(refreshToken, csrfToken()).andExpect(status().isUnauthorized());
    }

    @Test
    void loggingOutNeedsTheCsrfToken() throws Exception {
        String refreshToken = cookieOf(login(), SessionCookies.REFRESH).getValue();

        mvc.perform(post("/auth/logout").cookie(new Cookie(SessionCookies.REFRESH, refreshToken)))
                .andExpect(status().isForbidden());
        refresh(refreshToken, csrfToken()).andExpect(status().isNoContent());
    }

    @Test
    void loggingOutWithoutASessionStillSucceeds() throws Exception {
        mvc.perform(withCsrf(post("/auth/logout"), csrfToken())).andExpect(status().isNoContent());
    }

    @Test
    void aWrongPasswordSetsNoSessionCookies() throws Exception {
        String csrf = csrfToken();

        MockHttpServletResponse response = mvc.perform(withCsrf(post("/auth/login"), csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ann@example.com\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse();

        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                .noneMatch(header -> header.startsWith(SessionCookies.ACCESS) || header.startsWith(SessionCookies.REFRESH));
    }

    @Test
    void aSessionOutlivesNothingForAUserWhoWasDeleted() throws Exception {
        String refreshToken = cookieOf(login(), SessionCookies.REFRESH).getValue();
        users.deleteById(ann.getId());
        users.flush();

        refresh(refreshToken, csrfToken()).andExpect(status().isUnauthorized());
        assertThat(List.of(refreshToken)).isNotEmpty();
    }
}
