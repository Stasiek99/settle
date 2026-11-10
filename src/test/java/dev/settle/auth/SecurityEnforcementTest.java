package dev.settle.auth;

import dev.settle.TestcontainersConfiguration;
import dev.settle.support.TestEntities;
import dev.settle.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SecurityEnforcementTest {

    private static final String ID = "00000000-0000-0000-0000-000000000001";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtProperties properties;

    private final SecurityConfig config = new SecurityConfig();

    private String tokenIssuedAt(Clock clock, String secret, String issuer) {
        JwtProperties tokenProperties = new JwtProperties(secret, issuer, properties.expiration());
        User user = TestEntities.user("ann@example.com", "Ann");
        return new JwtService(config.jwtEncoder(tokenProperties), tokenProperties, clock)
                .issueFor(user).accessToken();
    }

    private String validToken() {
        return tokenIssuedAt(Clock.systemUTC(), properties.secret(), properties.issuer());
    }

    private ResultActions send(String method, String path, String bearer) throws Exception {
        var builder = request(HttpMethod.valueOf(method), path.replace("{id}", ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}");
        if (bearer != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
        }
        return mvc.perform(builder);
    }

    @ParameterizedTest(name = "{0} {1} requires a token")
    @CsvSource({
            "GET,/users/me",
            "GET,/groups",
            "POST,/groups",
            "GET,/groups/{id}",
            "GET,/groups/{id}/members",
            "POST,/groups/{id}/members",
            "GET,/groups/{id}/expenses",
            "POST,/groups/{id}/expenses",
            "GET,/groups/{id}/expenses/{id}",
            "PUT,/groups/{id}/expenses/{id}",
            "DELETE,/groups/{id}/expenses/{id}",
            "GET,/groups/{id}/balances",
            "GET,/groups/{id}/settlements",
            "POST,/groups/{id}/settlements",
            "GET,/groups/{id}/settlements/{id}",
            "GET,/groups/{id}/settlements/suggestions",
            "GET,/actuator/info",
            "GET,/does-not-exist"
    })
    void protectedEndpointsRejectRequestsWithoutAToken(String method, String path) throws Exception {
        send(method, path, null)
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("Authentication is required"));
    }

    @Test
    void healthAndAuthEndpointsStayPublic() throws Exception {
        send("GET", "/actuator/health", null).andExpect(status().isOk());
        send("POST", "/auth/register", null).andExpect(status().isBadRequest());
        send("POST", "/auth/login", null).andExpect(status().isBadRequest());
    }

    @Test
    void aValidTokenOpensProtectedEndpoints() throws Exception {
        send("GET", "/users/me", validToken()).andExpect(status().isNotFound());
        send("GET", "/groups/{id}/balances", validToken()).andExpect(status().isNotFound());
    }

    @Test
    void garbageAndForgedTokensAreRejectedWithAJsonBody() throws Exception {
        send("GET", "/users/me", "garbage")
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("invalid_token")))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value("The access token is invalid or has expired"));
        send("GET", "/users/me", validToken() + "x").andExpect(status().isUnauthorized());
    }

    @Test
    void anExpiredTokenIsRejected() throws Exception {
        Clock twoHoursAgo = Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC);
        String expired = tokenIssuedAt(twoHoursAgo, properties.secret(), properties.issuer());

        send("GET", "/users/me", expired)
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("invalid_token")));
    }

    @Test
    void aTokenSignedWithAnotherKeyOrIssuedByAnotherIssuerIsRejected() throws Exception {
        String foreignKey = tokenIssuedAt(Clock.systemUTC(), "a-completely-different-secret-of-32-chars-or-more", properties.issuer());
        String foreignIssuer = tokenIssuedAt(Clock.systemUTC(), properties.secret(), "someone-else");

        send("GET", "/users/me", foreignKey).andExpect(status().isUnauthorized());
        send("GET", "/users/me", foreignIssuer).andExpect(status().isUnauthorized());
    }

    @Test
    void aStaleTokenDoesNotBlockLoggingInAgain() throws Exception {
        Clock twoHoursAgo = Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC);
        String expired = tokenIssuedAt(twoHoursAgo, properties.secret(), properties.issuer());

        send("POST", "/auth/login", expired).andExpect(status().isBadRequest());
        send("POST", "/auth/register", "garbage").andExpect(status().isBadRequest());
    }

    @Test
    void theErrorBodyNeverEchoesTheRequestedPathOrToken() throws Exception {
        send("GET", "/users/me", "secret-looking-token-value")
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("secret-looking-token-value"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString(UUID.randomUUID().toString()))));
    }
}
