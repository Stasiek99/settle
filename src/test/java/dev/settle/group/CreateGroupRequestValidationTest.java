package dev.settle.group;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CreateGroupRequestValidationTest {

    private static final UUID SOME_USER = UUID.randomUUID();

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
    void acceptsValidRequestWithLowerCaseCurrency() {
        assertThat(validator.validate(new CreateGroupRequest("Flat", "pln", SOME_USER))).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "PL", "PLNN", "P1N", "zł!"})
    void rejectsInvalidCurrency(String currency) {
        var violations = validator.validate(new CreateGroupRequest("Flat", currency, SOME_USER));

        assertThat(violations).extracting(v -> v.getPropertyPath().toString()).contains("currency");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsBlankName(String name) {
        var violations = validator.validate(new CreateGroupRequest(name, "PLN", SOME_USER));

        assertThat(violations).extracting(v -> v.getPropertyPath().toString()).contains("name");
    }

    @Test
    void requiresCreator() {
        var violations = validator.validate(new CreateGroupRequest("Flat", "PLN", null));

        assertThat(violations).extracting(v -> v.getPropertyPath().toString()).containsExactly("createdBy");
    }
}
