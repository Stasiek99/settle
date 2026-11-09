package dev.settle.group;

import dev.settle.TestcontainersConfiguration;
import dev.settle.user.User;
import dev.settle.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class GroupRotationCursorTest {

    @Autowired
    private UserRepository users;

    @Autowired
    private GroupRepository groups;

    @Autowired
    private EntityManager em;

    private Group group;

    @BeforeEach
    void createGroup() {
        User ann = users.save(new User("ann@example.com", "Ann"));
        group = groups.saveAndFlush(new Group("Flat", "PLN", ann));
        em.clear();
    }

    @Test
    void newGroupStartsAtZero() {
        assertThat(groups.findById(group.getId()).orElseThrow().getRotationCursor()).isZero();
    }

    @Test
    void advanceAddsToTheCurrentValue() {
        assertThat(groups.advanceRotationCursor(group.getId(), 2)).isEqualTo(1);
        assertThat(groups.advanceRotationCursor(group.getId(), 3)).isEqualTo(1);
        em.clear();

        assertThat(groups.findById(group.getId()).orElseThrow().getRotationCursor()).isEqualTo(5);
    }

    @Test
    void advancingByZeroLeavesTheCursorAlone() {
        groups.advanceRotationCursor(group.getId(), 0);
        em.clear();

        assertThat(groups.findById(group.getId()).orElseThrow().getRotationCursor()).isZero();
    }

    @Test
    void advanceReportsZeroUpdatedRowsForUnknownGroup() {
        assertThat(groups.advanceRotationCursor(UUID.randomUUID(), 1)).isZero();
    }

    @Test
    void lockedLoadReturnsTheCurrentCursor() {
        groups.advanceRotationCursor(group.getId(), 4);
        em.clear();

        Group locked = groups.findByIdForUpdate(group.getId()).orElseThrow();

        assertThat(locked.getRotationCursor()).isEqualTo(4);
    }
}
