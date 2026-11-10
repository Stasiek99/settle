package dev.settle.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.settle.TestcontainersConfiguration;
import dev.settle.support.TestAuth;
import dev.settle.user.User;
import dev.settle.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class RequestLoggingTest {

    private static final String ACCESS_LOGGER = "dev.settle.access";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    private final Logger appLogger = (Logger) LoggerFactory.getLogger("dev.settle");
    private ListAppender<ILoggingEvent> appender;
    private User ann;

    @BeforeEach
    void captureLogs() {
        appender = new ListAppender<>();
        appender.start();
        appLogger.addAppender(appender);
        ann = users.save(new User("ann@example.com", "Ann"));
    }

    @AfterEach
    void stopCapturing() {
        appLogger.detachAppender(appender);
        MDC.clear();
    }

    private List<ILoggingEvent> accessEvents() {
        return appender.list.stream().filter(e -> e.getLoggerName().equals(ACCESS_LOGGER)).toList();
    }

    private static Object keyValue(ILoggingEvent event, String key) {
        return event.getKeyValuePairs().stream()
                .filter(kv -> kv.key.equals(key)).map(kv -> kv.value).findFirst().orElse(null);
    }

    @Test
    void everyResponseCarriesAGeneratedRequestId() throws Exception {
        String id = mvc.perform(get("/actuator/health"))
                .andExpect(header().exists(RequestLoggingFilter.REQUEST_ID_HEADER))
                .andReturn().getResponse().getHeader(RequestLoggingFilter.REQUEST_ID_HEADER);

        assertThat(id).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    void aSafeInboundRequestIdIsKeptAndEchoed() throws Exception {
        mvc.perform(get("/groups").header(RequestLoggingFilter.REQUEST_ID_HEADER, "client-trace_01.a"))
                .andExpect(header().string(RequestLoggingFilter.REQUEST_ID_HEADER, "client-trace_01.a"));

        assertThat(accessEvents()).singleElement()
                .satisfies(e -> assertThat(e.getMDCPropertyMap()).containsEntry("requestId", "client-trace_01.a"));
    }

    @ParameterizedTest(name = "inbound request id [{0}] is replaced")
    @ValueSource(strings = {"has space", "line\nbreak forged=entry", "<script>", "a;b", "ünïcode",
            "0123456789012345678901234567890123456789012345678901234567890123456789"})
    void anUnsafeInboundRequestIdIsReplaced(String inbound) throws Exception {
        String returned = mvc.perform(get("/groups").header(RequestLoggingFilter.REQUEST_ID_HEADER, inbound))
                .andReturn().getResponse().getHeader(RequestLoggingFilter.REQUEST_ID_HEADER);

        assertThat(returned).isNotEqualTo(inbound).matches("[0-9a-f-]{36}");
        assertThat(accessEvents()).allSatisfy(e -> assertThat(e.getFormattedMessage()).doesNotContain(inbound));
    }

    @Test
    void logsOneAccessLineWithMethodPathStatusAndDuration() throws Exception {
        TestAuth.authenticateAs(ann.getId());

        mvc.perform(get("/groups")).andExpect(status().isOk());

        assertThat(accessEvents()).singleElement().satisfies(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.INFO);
            assertThat(e.getFormattedMessage()).startsWith("HTTP GET /groups -> 200 in ");
            assertThat(keyValue(e, "http.request.method")).isEqualTo("GET");
            assertThat(keyValue(e, "url.path")).isEqualTo("/groups");
            assertThat(keyValue(e, "http.response.status_code")).isEqualTo(200);
            assertThat(keyValue(e, "event.duration_ms")).isInstanceOf(Long.class);
        });
    }

    @Test
    void anAuthenticatedRequestIsTaggedWithTheUserId() throws Exception {
        TestAuth.authenticateAs(ann.getId());

        mvc.perform(get("/groups")).andExpect(status().isOk());

        assertThat(accessEvents()).singleElement()
                .satisfies(e -> assertThat(e.getMDCPropertyMap()).containsEntry("userId", ann.getId().toString()));
    }

    @Test
    void anAnonymousRequestHasNoUserId() throws Exception {
        mvc.perform(get("/groups")).andExpect(status().isUnauthorized());

        assertThat(accessEvents()).singleElement().satisfies(e -> {
            assertThat(e.getMDCPropertyMap()).doesNotContainKey("userId").containsKey("requestId");
            assertThat(e.getFormattedMessage()).contains("-> 401");
        });
    }

    @Test
    void neverLogsQueryStringsOrCredentials() throws Exception {
        TestAuth.authenticateAs(ann.getId());

        mvc.perform(get("/groups")
                .param("access_token", "super-secret-token")
                .header(HttpHeaders.AUTHORIZATION, "Bearer another-secret-token"));

        assertThat(appender.list).allSatisfy(e -> {
            String text = e.getFormattedMessage() + e.getKeyValuePairs() + e.getMDCPropertyMap();
            assertThat(text).doesNotContain("secret-token").doesNotContain("access_token");
        });
    }

    @Test
    void theHealthCheckIsNotLogged() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());

        assertThat(accessEvents()).isEmpty();
    }

    @Test
    void theMdcIsClearedWhenTheRequestEnds() throws Exception {
        TestAuth.authenticateAs(ann.getId());

        mvc.perform(get("/groups")).andExpect(status().isOk());

        assertThat(MDC.get("requestId")).isNull();
        assertThat(MDC.get("userId")).isNull();
    }

    @Test
    void aFailedLoginIsWarnedAboutWithoutTheSubmittedEmailOrPassword() throws Exception {
        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\",\"password\":\"hunter2-hunter2\"}"))
                .andExpect(status().isUnauthorized());

        assertThat(appender.list).anySatisfy(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.WARN);
            assertThat(e.getFormattedMessage()).contains("Login failed");
        });
        assertThat(appender.list).allSatisfy(e -> {
            String text = e.getFormattedMessage() + e.getKeyValuePairs() + e.getMDCPropertyMap();
            assertThat(text).doesNotContain("nobody@example.com").doesNotContain("hunter2");
        });
    }

    @Test
    void aRegistrationIsLoggedByUserIdOnly() throws Exception {
        mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new@example.com\",\"displayName\":\"Newbie\",\"password\":\"a-long-enough-password\"}"))
                .andExpect(status().isCreated());

        assertThat(appender.list).anySatisfy(e -> assertThat(e.getFormattedMessage()).isEqualTo("User registered"));
        assertThat(appender.list).allSatisfy(e -> {
            String text = e.getFormattedMessage() + e.getKeyValuePairs() + e.getMDCPropertyMap();
            assertThat(text).doesNotContain("new@example.com").doesNotContain("a-long-enough-password");
        });
    }

    @Test
    void anUnhandledFailureIsLoggedAtErrorLevelAndStillClearsTheMdc() {
        RequestLoggingFilter filter = new RequestLoggingFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/boom");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain failing = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                throw new IllegalStateException("kaboom");
            }
        };

        assertThatThrownBy(() -> filter.doFilter(request, response, failing))
                .isInstanceOf(IllegalStateException.class);

        assertThat(accessEvents()).singleElement().satisfies(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.ERROR);
            assertThat(e.getFormattedMessage()).contains("/boom").contains("500");
        });
        assertThat(MDC.getCopyOfContextMap()).satisfiesAnyOf(
                map -> assertThat(map).isNull(),
                map -> assertThat((Map<String, String>) map).doesNotContainKey("requestId"));
    }
}
