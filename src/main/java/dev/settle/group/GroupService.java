package dev.settle.group;

import dev.settle.common.ConflictException;
import dev.settle.common.PageQuery;
import dev.settle.common.PageResponse;
import dev.settle.common.ResourceNotFoundException;
import dev.settle.user.User;
import dev.settle.user.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class GroupService {

    private static final Sort NEWEST_FIRST =
            Sort.by(Sort.Order.desc("group.createdAt"), Sort.Order.asc("group.id"));

    private final GroupRepository groups;
    private final MembershipRepository memberships;
    private final UserRepository users;

    public GroupService(GroupRepository groups, MembershipRepository memberships, UserRepository users) {
        this.groups = groups;
        this.memberships = memberships;
        this.users = users;
    }

    @Transactional
    public GroupResponse create(CreateGroupRequest request, UUID creatorId) {
        User creator = findUser(creatorId);
        Group group = groups.save(new Group(request.name(), request.currency(), creator));
        memberships.save(new Membership(group, creator, MembershipRole.OWNER));
        return GroupResponse.from(group);
    }

    public PageResponse<GroupResponse> listFor(UUID userId, PageQuery query) {
        return PageResponse.of(
                memberships.findByUserId(userId, query.toPageable(NEWEST_FIRST)),
                membership -> GroupResponse.from(membership.getGroup()));
    }

    public GroupResponse get(UUID groupId) {
        return GroupResponse.from(findGroup(groupId));
    }

    @Transactional
    public MembershipResponse addMember(UUID groupId, AddMemberRequest request) {
        Group group = findGroup(groupId);
        User user = findUser(request.userId());
        if (memberships.existsByGroupIdAndUserId(groupId, user.getId())) {
            throw new ConflictException("User is already a member of this group");
        }
        Membership saved = memberships.save(new Membership(group, user, MembershipRole.MEMBER));
        return MembershipResponse.from(saved);
    }

    public List<MembershipResponse> listMembers(UUID groupId) {
        findGroup(groupId);
        return memberships.findAllByGroupId(groupId).stream()
                .map(MembershipResponse::from)
                .toList();
    }

    private Group findGroup(UUID id) {
        return groups.findById(id).orElseThrow(() -> new ResourceNotFoundException("Group", id));
    }

    private User findUser(UUID id) {
        return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
    }
}
