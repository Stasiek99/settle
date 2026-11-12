package dev.settle.auth;

import dev.settle.TestcontainersConfiguration;
import dev.settle.user.User;
import dev.settle.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "settle.security.login-rate-limit.max-per-client-and-account=3",
                "settle.security.login-rate-limit.max-per-client=1000",
                "settle.security.login-rate-limit.max-per-account=1000"
        })
@Import(TestcontainersConfiguration.class)
class LoginRateLimitOnTheWireTest {

    private static final String PASSWORD = "correct-horse-battery";

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final HttpClient client = HttpClient.newHttpClient();
    private User ann;
    private String email;
    private String csrf;

    @BeforeEach
    void createUser() throws Exception {
        email = UUID.randomUUID() + "@example.com";
        ann = users.save(new User(email, "Ann", passwordEncoder.encode(PASSWORD)));
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/auth/csrf")).build(),
                HttpResponse.BodyHandlers.ofString());
        String cookie = response.headers().allValues("Set-Cookie").stream()
                .filter(header -> header.startsWith("XSRF-TOKEN=")).findFirst().orElseThrow();
        csrf = cookie.substring(cookie.indexOf('=') + 1, cookie.indexOf(';'));
    }

    @AfterEach
    void cleanUp() {
        users.deleteById(ann.getId());
    }

    private HttpResponse<String> login(String loginEmail, String password, String forwardedFor) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/auth/login"))
                .header("Content-Type", "application/json")
                .header("Cookie", "XSRF-TOKEN=" + csrf)
                .header("X-XSRF-TOKEN", csrf)
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"email":"%s","password":"%s"}
                        """.formatted(loginEmail, password)));
        if (forwardedFor != null) {
            request.header("X-Forwarded-For", forwardedFor);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void failTimes(int times, String forwardedFor) throws Exception {
        for (int i = 0; i < times; i++) {
            assertThat(login(email, "wrong-password", forwardedFor).statusCode()).isEqualTo(401);
        }
    }

    @Test
    void blocksFurtherAttemptsWithRetryAfterOnceTheLimitIsReached() throws Exception {
        failTimes(3, "198.51.100.1");

        HttpResponse<String> blocked = login(email, "wrong-password", "198.51.100.1");

        assertThat(blocked.statusCode()).isEqualTo(429);
        assertThat(Long.parseLong(blocked.headers().firstValue("Retry-After").orElseThrow())).isPositive();
        assertThat(blocked.body()).contains("Too many failed sign-in attempts");
    }

    @Test
    void theCorrectPasswordIsRefusedWhileBlocked() throws Exception {
        failTimes(3, "198.51.100.2");

        assertThat(login(email, PASSWORD, "198.51.100.2").statusCode()).isEqualTo(429);
    }

    @Test
    void anUnknownAccountIsBlockedTheSameWayAsAKnownOne() throws Exception {
        String unknown = UUID.randomUUID() + "@example.com";
        for (int i = 0; i < 3; i++) {
            assertThat(login(unknown, "wrong-password", "198.51.100.3").statusCode()).isEqualTo(401);
        }

        assertThat(login(unknown, "wrong-password", "198.51.100.3").statusCode()).isEqualTo(429);
    }

    @Test
    void aForgedLeftmostForwardedForEntryDoesNotEscapeTheLimit() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(login(email, "wrong-password", "10.1.1." + i + ", 198.51.100.4").statusCode()).isEqualTo(401);
        }

        assertThat(login(email, "wrong-password", "172.31.9.9, 198.51.100.4").statusCode()).isEqualTo(429);
    }

    @Test
    void anotherClientAddressIsCountedSeparately() throws Exception {
        failTimes(3, "198.51.100.5");

        assertThat(login(email, PASSWORD, "198.51.100.6").statusCode()).isEqualTo(200);
    }

    @Test
    void aSuccessfulLoginResetsTheStreakOfFailures() throws Exception {
        failTimes(2, "198.51.100.7");
        assertThat(login(email, PASSWORD, "198.51.100.7").statusCode()).isEqualTo(200);

        failTimes(2, "198.51.100.7");
    }
}
