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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MockMvc's response only renders SameSite for its own cookie class, so the attributes that matter for
 * security are checked here against a real Tomcat.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class SessionCookiesOnTheWireTest {

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

    @BeforeEach
    void createUser() {
        email = UUID.randomUUID() + "@example.com";
        ann = users.save(new User(email, "Ann", passwordEncoder.encode(PASSWORD)));
    }

    @AfterEach
    void cleanUp() {
        users.deleteById(ann.getId());
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder to(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    }

    private static String cookie(HttpResponse<?> response, String name) {
        return response.headers().allValues("Set-Cookie").stream()
                .filter(header -> header.startsWith(name + "=")).findFirst().orElseThrow();
    }

    private static String valueOf(String setCookie) {
        return setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
    }

    @Test
    void theCsrfCookieIsStrictSecureAndReadableByScripts() throws Exception {
        HttpResponse<String> response = send(to("/auth/csrf"));

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(cookie(response, "XSRF-TOKEN")).contains("SameSite=Strict", "Secure", "Path=/")
                .doesNotContain("HttpOnly");
    }

    @Test
    void theSessionCookiesAreHttpOnlyStrictSecureAndScopedToTheApi() throws Exception {
        String csrf = valueOf(cookie(send(to("/auth/csrf")), "XSRF-TOKEN"));

        HttpResponse<String> login = send(to("/auth/login")
                .header("Content-Type", "application/json")
                .header("Cookie", "XSRF-TOKEN=" + csrf)
                .header("X-XSRF-TOKEN", csrf)
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))));

        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(cookie(login, SessionCookies.ACCESS))
                .contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api", "Max-Age=900");
        assertThat(cookie(login, SessionCookies.REFRESH))
                .contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/auth", "Max-Age=604800");
        List<String> all = login.headers().allValues("Set-Cookie");
        assertThat(all).noneMatch(header -> header.startsWith("JSESSIONID"));
        assertThat(login.body()).doesNotContain("eyJ");
    }
}
