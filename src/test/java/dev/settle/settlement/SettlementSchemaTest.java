package dev.settle.settlement;

import dev.settle.TestcontainersConfiguration;
import dev.settle.group.Group;
import dev.settle.group.GroupRepository;
import dev.settle.user.User;
import dev.settle.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class SettlementSchemaTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository users;

    @Autowired
    private GroupRepository groups;

    @Autowired
    private EntityManager em;

    private UUID groupId;
    private UUID annId;
    private UUID bobId;

    @BeforeEach
    void createGroup() {
        User ann = users.save(new User("ann@example.com", "Ann"));
        User bob = users.save(new User("bob@example.com", "Bob"));
        Group group = groups.save(new Group("Flat", "PLN", ann));
        em.flush();
        annId = ann.getId();
        bobId = bob.getId();
        groupId = group.getId();
    }

    private void insert(UUID group, UUID paidBy, UUID paidTo, long amount, String currency) {
        jdbc.update("insert into settlements (group_id, paid_by, paid_to, amount_minor, currency) values (?, ?, ?, ?, ?)",
                group, paidBy, paidTo, amount, currency);
    }

    @Test
    void acceptsAValidSettlement() {
        insert(groupId, bobId, annId, 100, "PLN");
    }

    @Test
    void rejectsAZeroAmount() {
        assertThatThrownBy(() -> insert(groupId, bobId, annId, 0, "PLN"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsANegativeAmount() {
        assertThatThrownBy(() -> insert(groupId, bobId, annId, -5, "PLN"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsPayingYourself() {
        assertThatThrownBy(() -> insert(groupId, annId, annId, 100, "PLN"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsALowerCaseCurrency() {
        assertThatThrownBy(() -> insert(groupId, bobId, annId, 100, "pln"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsAnUnknownGroup() {
        assertThatThrownBy(() -> insert(UUID.randomUUID(), bobId, annId, 100, "PLN"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
