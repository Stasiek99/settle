package dev.settle.auth;

import dev.settle.common.TooManyRequestsException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginRateLimiterTest {

    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final AtomicLong nanos = new AtomicLong();
    private final LoginRateLimiter limiter = new LoginRateLimiter(
            new LoginRateLimitProperties(WINDOW, 3, 5, 6, 1000), nanos::get);

    private void fail(String ip, String email, int times) {
        for (int i = 0; i < times; i++) {
            limiter.requireAllowed(ip, email);
            limiter.recordFailure(ip, email);
        }
    }

    private void advance(Duration duration) {
        nanos.addAndGet(duration.toNanos());
    }

    @Test
    void allowsAttemptsUntilTheLimitForOneClientAndAccountIsReached() {
        fail("1.1.1.1", "ann@example.com", 3);

        assertThatThrownBy(() -> limiter.requireAllowed("1.1.1.1", "ann@example.com"))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void anotherAccountFromTheSameClientIsNotBlockedByThePairLimit() {
        fail("1.1.1.1", "ann@example.com", 3);

        assertThatCode(() -> limiter.requireAllowed("1.1.1.1", "bob@example.com")).doesNotThrowAnyException();
    }

    @Test
    void theSameAccountFromAnotherClientIsNotBlockedByThePairLimit() {
        fail("1.1.1.1", "ann@example.com", 3);

        assertThatCode(() -> limiter.requireAllowed("2.2.2.2", "ann@example.com")).doesNotThrowAnyException();
    }

    @Test
    void oneClientTryingManyAccountsIsBlockedByTheClientLimit() {
        for (int i = 0; i < 5; i++) {
            fail("1.1.1.1", "user" + i + "@example.com", 1);
        }

        assertThatThrownBy(() -> limiter.requireAllowed("1.1.1.1", "fresh@example.com"))
                .isInstanceOf(TooManyRequestsException.class);
        assertThatCode(() -> limiter.requireAllowed("2.2.2.2", "fresh@example.com")).doesNotThrowAnyException();
    }

    @Test
    void oneAccountAttackedFromManyClientsIsBlockedByTheAccountLimit() {
        for (int i = 0; i < 6; i++) {
            fail("10.0.0." + i, "ann@example.com", 1);
        }

        assertThatThrownBy(() -> limiter.requireAllowed("9.9.9.9", "ann@example.com"))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void emailsAreMatchedIgnoringCaseAndSurroundingSpaces() {
        fail("1.1.1.1", "Ann@Example.com", 2);
        fail("1.1.1.1", "  ann@example.com ", 1);

        assertThatThrownBy(() -> limiter.requireAllowed("1.1.1.1", "ANN@example.com"))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void reportsHowLongIsLeftOfTheWindowThatStartedWithTheFirstFailure() {
        fail("1.1.1.1", "ann@example.com", 1);
        advance(Duration.ofMinutes(10));
        fail("1.1.1.1", "ann@example.com", 2);

        assertThatThrownBy(() -> limiter.requireAllowed("1.1.1.1", "ann@example.com"))
                .isInstanceOfSatisfying(TooManyRequestsException.class,
                        e -> assertThat(e.getRetryAfter()).isEqualTo(Duration.ofMinutes(5)));
    }

    @Test
    void laterFailuresDoNotExtendTheBlock() {
        fail("1.1.1.1", "ann@example.com", 3);
        advance(Duration.ofMinutes(14));
        assertThatThrownBy(() -> limiter.requireAllowed("1.1.1.1", "ann@example.com"))
                .isInstanceOf(TooManyRequestsException.class);
        limiter.recordFailure("1.1.1.1", "ann@example.com");

        advance(Duration.ofMinutes(1));

        assertThatCode(() -> limiter.requireAllowed("1.1.1.1", "ann@example.com")).doesNotThrowAnyException();
    }

    @Test
    void theBlockEndsWhenTheWindowHasPassedAndCountingStartsAgain() {
        fail("1.1.1.1", "ann@example.com", 3);

        advance(WINDOW);

        fail("1.1.1.1", "ann@example.com", 2);
        assertThatCode(() -> limiter.requireAllowed("1.1.1.1", "ann@example.com")).doesNotThrowAnyException();
    }

    @Test
    void aSuccessfulLoginClearsTheCountForThatClientAndAccount() {
        fail("1.1.1.1", "ann@example.com", 2);

        limiter.recordSuccess("1.1.1.1", "ann@example.com");

        fail("1.1.1.1", "ann@example.com", 2);
        assertThatCode(() -> limiter.requireAllowed("1.1.1.1", "ann@example.com")).doesNotThrowAnyException();
    }

    @Test
    void aSuccessDoesNotClearWhatAnAttackerBuiltUpAgainstTheAccount() {
        for (int i = 0; i < 6; i++) {
            fail("10.0.0." + i, "ann@example.com", 1);
        }

        limiter.recordSuccess("1.1.1.1", "ann@example.com");

        assertThatThrownBy(() -> limiter.requireAllowed("1.1.1.1", "ann@example.com"))
                .isInstanceOf(TooManyRequestsException.class);
    }
}
