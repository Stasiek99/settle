package dev.settle.security;

import dev.settle.TestcontainersConfiguration;
import dev.settle.auth.JwtService;
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
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class HttpHardeningTest {

    private static final String PASSWORD = "correct-horse";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User ann;
    private String bearer;

    @BeforeEach
    void createUser() {
        ann = users.save(new User("ann@example.com", "Ann", passwordEncoder.encode(PASSWORD)));
        bearer = "Bearer " + jwtService.issueFor(ann).accessToken();
    }

    @Test
    void apiResponsesCarryTheStandardSecurityHeaders() throws Exception {
        mvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));
    }

    @Test
    void errorResponsesCarryThemToo() throws Exception {
        mvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));
    }

    @Test
    void aTokenResponseCanNeverBeCached() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ann@example.com\",\"password\":\"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));
    }

    @Test
    void onlyTheDocumentedCookiesAreEverIssuedAndNeverAServerSession() throws Exception {
        var responses = List.of(
                mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ann@example.com\",\"password\":\"%s\"}".formatted(PASSWORD))),
                mvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, bearer)),
                mvc.perform(get("/users/me")));

        for (var response : responses) {
            assertThat(response.andReturn().getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                    .allSatisfy(cookie -> assertThat(cookie).matches("^(XSRF-TOKEN|settle_access|settle_refresh)=.*"));
        }
    }

    @Test
    void crossOriginRequestsAreNotAllowedByDefault() throws Exception {
        mvc.perform(options("/groups")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        mvc.perform(get("/groups")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void aTokenInTheQueryStringIsIgnoredBecauseUrlsEndUpInLogs() throws Exception {
        mvc.perform(get("/users/me").param("access_token", bearer.substring("Bearer ".length())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void basicAuthenticationIsNotAccepted() throws Exception {
        String basic = Base64.getEncoder().encodeToString(
                ("ann@example.com:" + PASSWORD).getBytes(StandardCharsets.UTF_8));

        mvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Basic " + basic))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void formEncodedBodiesAreRefusedSoPlainHtmlFormsCannotPostToTheApi() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("email=ann@example.com&password=" + PASSWORD))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void unsupportedMethodsAreAnsweredWith405() throws Exception {
        mvc.perform(delete("/users/me").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isMethodNotAllowed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"env", "beans", "heapdump", "threaddump", "metrics", "loggers", "configprops",
            "mappings", "shutdown", "caches", "conditions", "scheduledtasks"})
    void sensitiveActuatorEndpointsAreNotReachableWithoutAToken(String endpoint) throws Exception {
        mvc.perform(get("/actuator/" + endpoint)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"env", "beans", "heapdump", "threaddump", "metrics", "loggers", "configprops",
            "mappings", "shutdown", "caches", "conditions", "scheduledtasks"})
    void sensitiveActuatorEndpointsDoNotExistEvenForALoggedInUser(String endpoint) throws Exception {
        mvc.perform(get("/actuator/" + endpoint).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNotFound());
    }

    @Test
    void thePublicHealthEndpointRevealsNothingAboutTheInfrastructure() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }
}
