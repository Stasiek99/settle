package dev.settle.auth;

import dev.settle.common.TooManyRequestsException;
import dev.settle.auth.AuthRateLimitProperties.Spec;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthRateLimiterTest {

    private final AtomicLong nanos = new AtomicLong();
    private final AuthRateLimiter limiter = new AuthRateLimiter(
            new AuthRateLimitProperties(
                    new Spec(Duration.ofHours(1), 2),
                    new Spec(Duration.ofMinutes(15), 3),
                    1000),
            nanos::get);

    @Test
    void blocksARegistrationOnceTheClientUsedItsAllowance() {
        limiter.countRegistration("1.1.1.1");
        limiter.countRegistration("1.1.1.1");

        assertThatThrownBy(() -> limiter.countRegistration("1.1.1.1"))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessage("Too many registrations. Try again in 60 minutes.");
    }

    @Test
    void otherClientsAreNotAffected() {
        limiter.countRegistration("1.1.1.1");
        limiter.countRegistration("1.1.1.1");

        assertThatCode(() -> limiter.countRegistration("2.2.2.2")).doesNotThrowAnyException();
    }

    @Test
    void registrationsAndRefreshesAreCountedSeparately() {
        limiter.countRegistration("1.1.1.1");
        limiter.countRegistration("1.1.1.1");

        assertThatCode(() -> limiter.countRefresh("1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void refreshesHaveTheirOwnLimit() {
        for (int i = 0; i < 3; i++) {
            limiter.countRefresh("1.1.1.1");
        }

        assertThatThrownBy(() -> limiter.countRefresh("1.1.1.1"))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageStartingWith("Too many session refreshes");
    }

    @Test
    void allowsAgainWhenTheWindowEnds() {
        limiter.countRegistration("1.1.1.1");
        limiter.countRegistration("1.1.1.1");
        nanos.addAndGet(Duration.ofHours(1).plusSeconds(1).toNanos());

        assertThatCode(() -> limiter.countRegistration("1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void tellsHowLongToWait() {
        limiter.countRegistration("1.1.1.1");
        limiter.countRegistration("1.1.1.1");
        nanos.addAndGet(Duration.ofMinutes(20).toNanos());

        assertThatThrownBy(() -> limiter.countRegistration("1.1.1.1"))
                .isInstanceOfSatisfying(TooManyRequestsException.class,
                        e -> assertThat(e.getRetryAfter()).isEqualTo(Duration.ofMinutes(40)));
    }
}
