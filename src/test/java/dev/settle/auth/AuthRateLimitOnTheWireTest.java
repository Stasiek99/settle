package dev.settle.auth;

import dev.settle.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "settle.security.rate-limit.register.max-per-client=2",
                "settle.security.rate-limit.refresh.max-per-client=2"
        })
@Import(TestcontainersConfiguration.class)
class AuthRateLimitOnTheWireTest {

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();
    private String csrf;

    @BeforeEach
    void fetchCsrfToken() throws Exception {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/auth/csrf")).build(),
                HttpResponse.BodyHandlers.ofString());
        String cookie = response.headers().allValues("Set-Cookie").stream()
                .filter(header -> header.startsWith("XSRF-TOKEN=")).findFirst().orElseThrow();
        csrf = cookie.substring(cookie.indexOf('=') + 1, cookie.indexOf(';'));
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .header("Cookie", "XSRF-TOKEN=" + csrf)
                .header("X-XSRF-TOKEN", csrf)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> register() throws Exception {
        return post("/auth/register", """
                {"email":"%s@example.com","displayName":"Ann","password":"correct-horse-battery"}
                """.formatted(UUID.randomUUID()));
    }

    @Test
    void registrationIsRefusedWithRetryAfterOnceTheClientUsedItsAllowance() throws Exception {
        assertThat(register().statusCode()).isEqualTo(201);
        assertThat(register().statusCode()).isEqualTo(201);

        HttpResponse<String> blocked = register();

        assertThat(blocked.statusCode()).isEqualTo(429);
        assertThat(blocked.headers().firstValue("Retry-After")).isPresent();
        assertThat(blocked.body()).contains("Too many registrations");
    }

    @Test
    void refreshIsRefusedOnceTheClientUsedItsAllowance() throws Exception {
        assertThat(post("/auth/refresh", "").statusCode()).isEqualTo(401);
        assertThat(post("/auth/refresh", "").statusCode()).isEqualTo(401);

        HttpResponse<String> blocked = post("/auth/refresh", "");

        assertThat(blocked.statusCode()).isEqualTo(429);
        assertThat(blocked.headers().firstValue("Retry-After")).isPresent();
    }
}
