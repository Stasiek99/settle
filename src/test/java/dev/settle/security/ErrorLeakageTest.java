package dev.settle.security;

import dev.settle.TestcontainersConfiguration;
import dev.settle.auth.JwtService;
import dev.settle.support.TestEntities;
import dev.settle.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ErrorLeakageTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private UserService userService;

    private HttpResponse<String> get(String path, String bearer) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void anUnexpectedFailureShowsTheClientNothingAboutTheInternals() throws Exception {
        when(userService.get(any(UUID.class)))
                .thenThrow(new IllegalStateException("jdbc:postgresql://db.internal:5432/settle password=hunter2"));
        String token = jwtService.issueFor(TestEntities.user("ann@example.com", "Ann")).accessToken();

        HttpResponse<String> response = get("/users/me", token);

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.body())
                .doesNotContain("hunter2")
                .doesNotContain("db.internal")
                .doesNotContain("IllegalStateException")
                .doesNotContain("dev.settle")
                .doesNotContain("at org.")
                .doesNotContain("stackTrace")
                .doesNotContain("trace");
    }

    @Test
    void anUnknownPathLooksTheSameWhetherOrNotItExists() throws Exception {
        assertThat(get("/definitely/not/here", null).statusCode()).isEqualTo(401);
        assertThat(get("/groups/ffffffff-ffff-ffff-ffff-ffffffffffff/nothing", null).statusCode()).isEqualTo(401);
    }

    @Test
    void pathTraversalIsStoppedByTheFirewallBeforeAnyRoutingHappens() throws Exception {
        assertThat(get("/groups/../../actuator/env", null).statusCode()).isEqualTo(400);
        assertThat(get("/groups/%2e%2e/%2e%2e/actuator/env", null).statusCode()).isEqualTo(400);
    }

    @Test
    void theErrorPageIsNotReachableDirectlyWithoutAToken() throws Exception {
        assertThat(get("/error", null).statusCode()).isEqualTo(401);
    }
}
