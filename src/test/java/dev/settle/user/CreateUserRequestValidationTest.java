package dev.settle.user;

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

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CreateUserRequestValidationTest {

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

    @Test
    void acceptsValidRequest() {
        assertThat(validator.validate(new CreateUserRequest("ann@example.com", "Ann"))).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "not-an-email", "ann@", "@example.com"})
    void rejectsInvalidEmail(String email) {
        Set<ConstraintViolation<CreateUserRequest>> violations =
                validator.validate(new CreateUserRequest(email, "Ann"));

        assertThat(violations).extracting(v -> v.getPropertyPath().toString()).contains("email");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsBlankDisplayName(String displayName) {
        Set<ConstraintViolation<CreateUserRequest>> violations =
                validator.validate(new CreateUserRequest("ann@example.com", displayName));

        assertThat(violations).extracting(v -> v.getPropertyPath().toString()).contains("displayName");
    }

    @Test
    void rejectsTooLongValues() {
        Set<ConstraintViolation<CreateUserRequest>> violations =
                validator.validate(new CreateUserRequest("a".repeat(250) + "@example.com", "x".repeat(101)));

        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .contains("email", "displayName");
    }
}
