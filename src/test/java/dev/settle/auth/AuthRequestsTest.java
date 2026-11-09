package dev.settle.auth;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AuthRequestsTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static <T> Set<String> invalidFields(T request) {
        Set<ConstraintViolation<T>> violations = validator.validate(request);
        return violations.stream().map(v -> v.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void acceptsAValidRegistration() {
        assertThat(invalidFields(new RegisterRequest("ann@example.com", "Ann", "correct-horse"))).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "not-an-email", "ann@", "@example.com"})
    void rejectsAnInvalidEmail(String email) {
        assertThat(invalidFields(new RegisterRequest(email, "Ann", "correct-horse"))).contains("email");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsABlankDisplayName(String displayName) {
        assertThat(invalidFields(new RegisterRequest("ann@example.com", displayName, "correct-horse")))
                .contains("displayName");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"        ", "short", "1234567"})
    void rejectsAWeakPassword(String password) {
        assertThat(invalidFields(new RegisterRequest("ann@example.com", "Ann", password))).contains("password");
    }

    @Test
    void acceptsPasswordsAtTheBoundaries() {
        assertThat(invalidFields(new RegisterRequest("ann@example.com", "Ann", "12345678"))).isEmpty();
        assertThat(invalidFields(new RegisterRequest("ann@example.com", "Ann", "x".repeat(72)))).isEmpty();
    }

    @Test
    void rejectsAPasswordLongerThanBcryptCanUse() {
        assertThat(invalidFields(new RegisterRequest("ann@example.com", "Ann", "x".repeat(73)))).contains("password");
    }

    @Test
    void loginRequiresBothFields() {
        assertThat(invalidFields(new LoginRequest(null, null))).containsExactlyInAnyOrder("email", "password");
        assertThat(invalidFields(new LoginRequest("ann@example.com", "secret"))).isEmpty();
    }

    @Test
    void jwtSecretMustBeAtLeast32Characters() {
        assertThat(invalidFields(new JwtProperties("too-short", "settle", Duration.ofHours(1)))).contains("secret");
        assertThat(invalidFields(new JwtProperties("x".repeat(32), "settle", Duration.ofHours(1)))).isEmpty();
    }

    @Test
    void secretsNeverAppearInToString() {
        assertThat(new RegisterRequest("ann@example.com", "Ann", "hunter2-hunter2").toString())
                .doesNotContain("hunter2");
        assertThat(new LoginRequest("ann@example.com", "hunter2-hunter2").toString()).doesNotContain("hunter2");
        assertThat(new TokenResponse("secret.jwt.token", "Bearer", 3600).toString()).doesNotContain("secret.jwt.token");
        assertThat(new JwtProperties("super-secret-signing-key-0123456789", "settle", Duration.ofHours(1)).toString())
                .doesNotContain("super-secret");
    }
}
