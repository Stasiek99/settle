package dev.settle.group;

import dev.settle.common.ConflictException;
import dev.settle.common.ResourceNotFoundException;
import dev.settle.support.TestEntities;
import dev.settle.user.User;
import dev.settle.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock
    private GroupRepository groups;

    @Mock
    private MembershipRepository memberships;

    @Mock
    private UserRepository users;

    @InjectMocks
    private GroupService service;

    private User ann;
    private User bob;
    private Group flat;

    @BeforeEach
    void setUp() {
        ann = TestEntities.user("ann@example.com", "Ann");
        bob = TestEntities.user("bob@example.com", "Bob");
        flat = TestEntities.group("Flat", "PLN", ann);
    }

    @Test
    void createSavesGroupAndMakesCreatorTheOwner() {
        when(users.findById(ann.getId())).thenReturn(Optional.of(ann));
        when(groups.save(any(Group.class))).thenAnswer(inv -> TestEntities.withId(inv.getArgument(0)));

        GroupResponse response = service.create(new CreateGroupRequest("Trip", "eur", ann.getId()));

        ArgumentCaptor<Membership> membership = ArgumentCaptor.forClass(Membership.class);
        verify(memberships).save(membership.capture());
        assertThat(membership.getValue().getUser()).isEqualTo(ann);
        assertThat(membership.getValue().getRole()).isEqualTo(MembershipRole.OWNER);
        assertThat(membership.getValue().getGroup().getId()).isEqualTo(response.id());
        assertThat(response.currency()).isEqualTo("EUR");
        assertThat(response.createdBy()).isEqualTo(ann.getId());
    }

    @Test
    void createFailsForUnknownCreatorAndWritesNothing() {
        UUID unknown = UUID.randomUUID();
        when(users.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new CreateGroupRequest("Trip", "EUR", unknown)))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(groups, memberships);
    }

    @Test
    void getThrowsNotFoundForUnknownGroup() {
        UUID id = UUID.randomUUID();
        when(groups.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void addMemberAddsUserWithMemberRole() {
        when(groups.findById(flat.getId())).thenReturn(Optional.of(flat));
        when(users.findById(bob.getId())).thenReturn(Optional.of(bob));
        when(memberships.existsByGroupIdAndUserId(flat.getId(), bob.getId())).thenReturn(false);
        when(memberships.save(any(Membership.class))).thenAnswer(inv -> TestEntities.withId(inv.getArgument(0)));

        MembershipResponse response = service.addMember(flat.getId(), new AddMemberRequest(bob.getId()));

        assertThat(response.userId()).isEqualTo(bob.getId());
        assertThat(response.displayName()).isEqualTo("Bob");
        assertThat(response.role()).isEqualTo(MembershipRole.MEMBER);
    }

    @Test
    void addMemberRejectsUserWhoIsAlreadyInTheGroup() {
        when(groups.findById(flat.getId())).thenReturn(Optional.of(flat));
        when(users.findById(bob.getId())).thenReturn(Optional.of(bob));
        when(memberships.existsByGroupIdAndUserId(flat.getId(), bob.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.addMember(flat.getId(), new AddMemberRequest(bob.getId())))
                .isInstanceOf(ConflictException.class);

        verify(memberships, never()).save(any());
    }

    @Test
    void addMemberFailsForUnknownGroup() {
        UUID unknown = UUID.randomUUID();
        when(groups.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addMember(unknown, new AddMemberRequest(bob.getId())))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(memberships, users);
    }

    @Test
    void addMemberFailsForUnknownUser() {
        UUID unknown = UUID.randomUUID();
        when(groups.findById(flat.getId())).thenReturn(Optional.of(flat));
        when(users.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addMember(flat.getId(), new AddMemberRequest(unknown)))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(memberships);
    }

    @Test
    void listMembersMapsMembershipsToResponses() {
        Membership owner = TestEntities.withId(new Membership(flat, ann, MembershipRole.OWNER));
        Membership member = TestEntities.withId(new Membership(flat, bob, MembershipRole.MEMBER));
        when(groups.findById(flat.getId())).thenReturn(Optional.of(flat));
        when(memberships.findAllByGroupId(flat.getId())).thenReturn(List.of(owner, member));

        List<MembershipResponse> result = service.listMembers(flat.getId());

        assertThat(result).extracting(MembershipResponse::displayName).containsExactly("Ann", "Bob");
        assertThat(result).extracting(MembershipResponse::role)
                .containsExactly(MembershipRole.OWNER, MembershipRole.MEMBER);
    }

    @Test
    void listMembersFailsForUnknownGroupInsteadOfReturningEmptyList() {
        UUID unknown = UUID.randomUUID();
        when(groups.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listMembers(unknown)).isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(memberships);
    }
}
