package dev.settle.group;

import dev.settle.cache.GroupCacheEvictor;
import dev.settle.common.ConflictException;
import dev.settle.common.PageQuery;
import dev.settle.common.PageResponse;
import dev.settle.common.ResourceNotFoundException;
import dev.settle.support.TestEntities;
import dev.settle.user.User;
import dev.settle.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @Mock
    private GroupCacheEvictor cacheEvictor;

    private GroupService service;

    private User ann;
    private User bob;
    private Group flat;

    @BeforeEach
    void setUp() {
        ann = TestEntities.user("ann@example.com", "Ann");
        bob = TestEntities.user("bob@example.com", "Bob");
        flat = TestEntities.group("Flat", "PLN", ann);
        service = new GroupService(groups, memberships, users, cacheEvictor, new GroupProperties(3));
    }

    @Test
    void createSavesGroupAndMakesCreatorTheOwner() {
        when(users.findById(ann.getId())).thenReturn(Optional.of(ann));
        when(groups.save(any(Group.class))).thenAnswer(inv -> TestEntities.withId(inv.getArgument(0)));

        GroupResponse response = service.create(new CreateGroupRequest("Trip", "eur"), ann.getId());

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

        assertThatThrownBy(() -> service.create(new CreateGroupRequest("Trip", "EUR"), unknown))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(groups, memberships);
    }

    @Test
    void listForMapsTheRequestedPageOfMemberships() {
        Group newer = TestEntities.group("Newer", "PLN", ann);
        Group older = TestEntities.group("Older", "PLN", ann);
        when(memberships.findByUserId(eq(ann.getId()), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(
                new Membership(newer, ann, MembershipRole.OWNER),
                new Membership(older, ann, MembershipRole.MEMBER)), PageRequest.of(1, 2), 5));

        PageResponse<GroupResponse> result = service.listFor(ann.getId(), new PageQuery(1, 2));

        assertThat(result.content()).extracting(GroupResponse::name).containsExactly("Newer", "Older");
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.totalElements()).isEqualTo(5);
        assertThat(result.totalPages()).isEqualTo(3);
    }

    @Test
    void listForIsEmptyForSomeoneWithoutGroups() {
        when(memberships.findByUserId(eq(bob.getId()), any(Pageable.class))).thenReturn(Page.empty());

        assertThat(service.listFor(bob.getId(), new PageQuery(null, null)).content()).isEmpty();
    }

    @Test
    void getThrowsNotFoundForUnknownGroup() {
        UUID id = UUID.randomUUID();
        when(groups.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void addMemberAddsUserWithMemberRole() {
        when(groups.findByIdForUpdate(flat.getId())).thenReturn(Optional.of(flat));
        when(users.findById(bob.getId())).thenReturn(Optional.of(bob));
        when(memberships.existsByGroupIdAndUserId(flat.getId(), bob.getId())).thenReturn(false);
        when(memberships.save(any(Membership.class))).thenAnswer(inv -> TestEntities.withId(inv.getArgument(0)));

        MembershipResponse response = service.addMember(flat.getId(), new AddMemberRequest(bob.getId()));

        assertThat(response.userId()).isEqualTo(bob.getId());
        assertThat(response.displayName()).isEqualTo("Bob");
        assertThat(response.role()).isEqualTo(MembershipRole.MEMBER);
        verify(cacheEvictor).evictGroupData(flat.getId());
    }

    @Test
    void addMemberRejectsUserWhoIsAlreadyInTheGroup() {
        when(groups.findByIdForUpdate(flat.getId())).thenReturn(Optional.of(flat));
        when(users.findById(bob.getId())).thenReturn(Optional.of(bob));
        when(memberships.existsByGroupIdAndUserId(flat.getId(), bob.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.addMember(flat.getId(), new AddMemberRequest(bob.getId())))
                .isInstanceOf(ConflictException.class);

        verify(memberships, never()).save(any());
        verify(cacheEvictor, never()).evictGroupData(any());
    }

    @Test
    void addMemberRejectsANewMemberWhenTheGroupIsFull() {
        when(groups.findByIdForUpdate(flat.getId())).thenReturn(Optional.of(flat));
        when(users.findById(bob.getId())).thenReturn(Optional.of(bob));
        when(memberships.existsByGroupIdAndUserId(flat.getId(), bob.getId())).thenReturn(false);
        when(memberships.countByGroupId(flat.getId())).thenReturn(3L);

        assertThatThrownBy(() -> service.addMember(flat.getId(), new AddMemberRequest(bob.getId())))
                .isInstanceOf(ConflictException.class)
                .hasMessage("A group can have at most 3 members");

        verify(memberships, never()).save(any());
        verify(cacheEvictor, never()).evictGroupData(any());
    }

    @Test
    void addMemberAcceptsTheLastFreeSlot() {
        when(groups.findByIdForUpdate(flat.getId())).thenReturn(Optional.of(flat));
        when(users.findById(bob.getId())).thenReturn(Optional.of(bob));
        when(memberships.existsByGroupIdAndUserId(flat.getId(), bob.getId())).thenReturn(false);
        when(memberships.countByGroupId(flat.getId())).thenReturn(2L);
        when(memberships.save(any(Membership.class))).thenAnswer(inv -> TestEntities.withId(inv.getArgument(0)));

        assertThat(service.addMember(flat.getId(), new AddMemberRequest(bob.getId())).userId())
                .isEqualTo(bob.getId());
    }

    @Test
    void addMemberFailsForUnknownGroup() {
        UUID unknown = UUID.randomUUID();
        when(groups.findByIdForUpdate(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addMember(unknown, new AddMemberRequest(bob.getId())))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(memberships, users);
    }

    @Test
    void addMemberFailsForUnknownUser() {
        UUID unknown = UUID.randomUUID();
        when(groups.findByIdForUpdate(flat.getId())).thenReturn(Optional.of(flat));
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
