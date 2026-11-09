package dev.settle.security;

import dev.settle.TestcontainersConfiguration;
import dev.settle.common.NoControlCharactersValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class NoControlCharactersTest {

    private final NoControlCharactersValidator validator = new NoControlCharactersValidator();

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void nullAndOrdinaryTextAreValid() {
        assertThat(validator.isValid(null, null)).isTrue();
        assertThat(validator.isValid("", null)).isTrue();
        assertThat(validator.isValid("Zażółć gęślą jaźń 😀 日本語 - ok!", null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"a\u0000b", "a\u0007b", "a\tb", "a\nb", "a\rb", "a\u001bb", "a\u007fb", "a\u0085b"})
    void controlCharactersAreInvalid(String text) {
        assertThat(validator.isValid(text, null)).isFalse();
    }

    @Test
    void postgresItselfRefusesNulCharactersSoWithoutValidationTheyWouldBecomeServerErrors() {
        assertThatThrownBy(() -> jdbc.update(
                "insert into users (email, display_name) values (?, ?)", "nul@example.com", "a\u0000b"))
                .isInstanceOf(DataAccessException.class);
    }
}
