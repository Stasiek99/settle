package dev.settle.group;

import dev.settle.TestcontainersConfiguration;
import dev.settle.user.User;
import dev.settle.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class GroupPersistenceTest {

    @Autowired
    private UserRepository users;

    @Autowired
    private GroupRepository groups;

    @Autowired
    private MembershipRepository memberships;

    @Autowired
    private EntityManager em;

    @Test
    void persistsGroupWithMembersAndReadsThemBack() {
        User ann = users.save(new User("  Ann@Example.com ", "Ann"));
        Group trip = groups.save(new Group("Trip to Rome", "eur", ann));
        memberships.save(new Membership(trip, ann, MembershipRole.OWNER));

        em.flush();
        em.clear();

        List<Membership> found = memberships.findAllByUserId(ann.getId());

        assertThat(found).hasSize(1);
        Membership membership = found.getFirst();
        assertThat(membership.getRole()).isEqualTo(MembershipRole.OWNER);
        assertThat(membership.getGroup().getName()).isEqualTo("Trip to Rome");
        assertThat(membership.getGroup().getCurrency()).isEqualTo("EUR");
    }

    @Test
    void normalisesEmailAndRejectsCaseInsensitiveDuplicates() {
        users.saveAndFlush(new User("Ann@Example.com", "Ann"));

        assertThat(users.findByEmail("ann@example.com")).isPresent();
        assertThatThrownBy(() -> users.saveAndFlush(new User("ANN@example.COM", "Other Ann")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void groupEntityNameWorksInJpql() {
        User ann = users.save(new User("ann@example.com", "Ann"));
        groups.save(new Group("Flat", "PLN", ann));
        em.flush();

        List<Group> result = em.createQuery("select g from Group g where g.name = :name", Group.class)
                .setParameter("name", "Flat")
                .getResultList();

        assertThat(result).hasSize(1);
    }
}
