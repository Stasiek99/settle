package dev.settle.group;

import dev.settle.cache.GroupCacheEvictor;
import dev.settle.common.ConflictException;
import dev.settle.common.PageQuery;
import dev.settle.common.PageResponse;
import dev.settle.common.PreconditionFailedException;
import dev.settle.common.ResourceNotFoundException;
import dev.settle.user.User;
import dev.settle.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@EnableConfigurationProperties(GroupProperties.class)
@Transactional(readOnly = true)
public class GroupService {

    private static final Logger log = LoggerFactory.getLogger(GroupService.class);

    private static final Sort NEWEST_FIRST =
            Sort.by(Sort.Order.desc("group.createdAt"), Sort.Order.asc("group.id"));

    private final GroupRepository groups;
    private final MembershipRepository memberships;
    private final UserRepository users;
    private final GroupCacheEvictor cacheEvictor;
    private final int maxMembers;

    public GroupService(GroupRepository groups, MembershipRepository memberships, UserRepository users,
                        GroupCacheEvictor cacheEvictor, GroupProperties properties) {
        this.groups = groups;
        this.memberships = memberships;
        this.users = users;
        this.cacheEvictor = cacheEvictor;
        this.maxMembers = properties.maxMembers();
    }

    @Transactional
    public GroupResponse create(CreateGroupRequest request, UUID creatorId) {
        User creator = findUser(creatorId);
        Group group = groups.save(new Group(request.name(), request.currency(), creator));
        memberships.save(new Membership(group, creator, MembershipRole.OWNER));
        log.atInfo().addKeyValue("group.id", group.getId()).addKeyValue("user.id", creatorId).log("Group created");
        return GroupResponse.from(group);
    }

    public PageResponse<GroupResponse> listFor(UUID userId, PageQuery query) {
        return PageResponse.of(
                memberships.findByUserId(userId, query.toPageable(NEWEST_FIRST)),
                membership -> GroupResponse.from(membership.getGroup()));
    }

    @Transactional
    public GroupResponse rename(UUID groupId, long expectedVersion, UpdateGroupRequest request) {
        Group group = findGroup(groupId);
        if (group.getVersion() != expectedVersion) {
            throw new PreconditionFailedException("The group was modified by someone else; reload it and retry");
        }
        group.rename(request.name());
        groups.flush();
        log.atInfo().addKeyValue("group.id", groupId).addKeyValue("group.version", group.getVersion())
                .log("Group renamed");
        return GroupResponse.from(group);
    }

    public GroupResponse get(UUID groupId) {
        return GroupResponse.from(findGroup(groupId));
    }

    @Transactional
    public MembershipResponse addMember(UUID groupId, AddMemberRequest request) {
        Group group = groups.findByIdForUpdate(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", groupId));
        User user = findUser(request.userId());
        if (memberships.existsByGroupIdAndUserId(groupId, user.getId())) {
            throw new ConflictException("User is already a member of this group");
        }
        if (memberships.countByGroupId(groupId) >= maxMembers) {
            throw new ConflictException("A group can have at most " + maxMembers + " members");
        }
        Membership saved = memberships.save(new Membership(group, user, MembershipRole.MEMBER));
        cacheEvictor.evictGroupData(groupId);
        log.atInfo().addKeyValue("group.id", groupId).addKeyValue("member.id", user.getId()).log("Member added");
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
