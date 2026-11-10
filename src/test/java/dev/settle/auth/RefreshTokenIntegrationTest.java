package dev.settle.auth;

import dev.settle.TestcontainersConfiguration;
import dev.settle.common.AuthenticationFailedException;
import dev.settle.support.MutableClock;
import dev.settle.user.User;
import dev.settle.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import({TestcontainersConfiguration.class, RefreshTokenIntegrationTest.TestClock.class})
class RefreshTokenIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClock {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(Instant.now());
        }
    }

    @Autowired
    private RefreshTokenService service;

    @Autowired
    private RefreshTokenRepository tokens;

    @Autowired
    private UserRepository users;

    @Autowired
    private MutableClock clock;

    private User user;

    @BeforeEach
    void createUser() {
        user = users.save(new User(UUID.randomUUID() + "@example.com", "Ann"));
    }

    @AfterEach
    void cleanUp() {
        tokens.deleteAll();
        users.deleteById(user.getId());
    }

    private UUID familyOf(RefreshTokenService.Issued issued) {
        return tokens.findByTokenHash(RefreshTokenService.hashOf(issued.value())).orElseThrow().getFamilyId();
    }

    @Test
    void onlyTheHashOfATokenIsStored() {
        RefreshTokenService.Issued issued = service.startSession(user.getId());

        assertThat(tokens.findAll()).allSatisfy(token -> {
            assertThat(token.getTokenHash()).hasSize(64).isNotEqualTo(issued.value());
            assertThat(token.toString()).doesNotContain(issued.value());
        });
        assertThat(tokens.findByTokenHash(issued.value())).isEmpty();
        assertThat(tokens.findByTokenHash(RefreshTokenService.hashOf(issued.value()))).isPresent();
    }

    @Test
    void aRotatedTokenIsRememberedAsUsed() {
        RefreshTokenService.Issued first = service.startSession(user.getId());

        service.rotate(first.value());

        assertThat(tokens.findByTokenHash(RefreshTokenService.hashOf(first.value())).orElseThrow().getUsedAt())
                .isNotNull();
    }

    @Test
    void stealingARotatedTokenKillsTheWholeSessionAndTheRevocationSurvivesTheRejection() {
        RefreshTokenService.Issued first = service.startSession(user.getId());
        RefreshTokenService.Issued second = service.rotate(first.value()).next();
        UUID family = familyOf(first);
        clock.advance(Duration.ofSeconds(30));

        assertThatThrownBy(() -> service.rotate(first.value())).isInstanceOf(AuthenticationFailedException.class);

        assertThat(tokens.countByFamilyIdAndRevokedAtIsNull(family)).isZero();
        assertThatThrownBy(() -> service.rotate(second.value())).isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void aSecondSessionOfTheSameUserIsUntouchedByTheTheftOfTheFirst() {
        RefreshTokenService.Issued phone = service.startSession(user.getId());
        RefreshTokenService.Issued laptop = service.startSession(user.getId());
        service.rotate(phone.value());
        clock.advance(Duration.ofSeconds(30));

        assertThatThrownBy(() -> service.rotate(phone.value())).isInstanceOf(AuthenticationFailedException.class);

        assertThat(service.rotate(laptop.value()).userId()).isEqualTo(user.getId());
    }

    @Test
    void revokingASessionEndsOnlyThatSession() {
        RefreshTokenService.Issued phone = service.startSession(user.getId());
        RefreshTokenService.Issued laptop = service.startSession(user.getId());

        service.revoke(phone.value());

        assertThatThrownBy(() -> service.rotate(phone.value())).isInstanceOf(AuthenticationFailedException.class);
        assertThat(service.rotate(laptop.value()).userId()).isEqualTo(user.getId());
    }

    @Test
    void ofTwoSimultaneousRefreshesWithTheSameTokenExactlyOneWinsAndTheSessionSurvives() throws Exception {
        RefreshTokenService.Issued first = service.startSession(user.getId());
        int contenders = 2;
        ExecutorService pool = Executors.newFixedThreadPool(contenders);
        CountDownLatch ready = new CountDownLatch(contenders);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<RefreshTokenService.Rotation>> results = new ArrayList<>();
            for (int i = 0; i < contenders; i++) {
                Callable<RefreshTokenService.Rotation> refresh = () -> {
                    ready.countDown();
                    go.await();
                    try {
                        return service.rotate(first.value());
                    } catch (AuthenticationFailedException e) {
                        return null;
                    }
                };
                results.add(pool.submit(refresh));
            }
            ready.await();
            go.countDown();

            List<RefreshTokenService.Rotation> winners = new ArrayList<>();
            for (Future<RefreshTokenService.Rotation> result : results) {
                RefreshTokenService.Rotation rotation = result.get(30, TimeUnit.SECONDS);
                if (rotation != null) {
                    winners.add(rotation);
                }
            }

            assertThat(winners).hasSize(1);
            assertThat(tokens.countByFamilyIdAndRevokedAtIsNull(familyOf(first))).isEqualTo(2);
            assertThat(service.rotate(winners.get(0).next().value()).userId()).isEqualTo(user.getId());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void purgingRemovesExpiredTokensAndKeepsTheLiveOnes() {
        RefreshTokenService.Issued old = service.startSession(user.getId());
        clock.advance(Duration.ofDays(8));
        RefreshTokenService.Issued fresh = service.startSession(user.getId());

        int removed = service.purgeExpired();

        assertThat(removed).isEqualTo(1);
        assertThat(tokens.findByTokenHash(RefreshTokenService.hashOf(old.value()))).isEmpty();
        assertThat(tokens.findByTokenHash(RefreshTokenService.hashOf(fresh.value()))).isPresent();
    }

    @Test
    void deletingAUserDeletesTheirSessions() {
        service.startSession(user.getId());
        service.startSession(user.getId());
        User other = users.save(new User(UUID.randomUUID() + "@example.com", "Bob"));
        service.startSession(other.getId());

        users.deleteById(user.getId());

        assertThat(tokens.findAll()).hasSize(1);
        assertThat(tokens.findAll().get(0).getUserId()).isEqualTo(other.getId());
        tokens.deleteAll();
        users.deleteById(other.getId());
    }
}
