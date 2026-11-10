package dev.settle.group;

import dev.settle.common.ForbiddenException;
import dev.settle.common.ResourceNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@Transactional(readOnly = true)
public class GroupAccess {

    private final MembershipRepository memberships;

    public GroupAccess(MembershipRepository memberships) {
        this.memberships = memberships;
    }

    public void requireMember(UUID groupId, UUID userId) {
        if (!memberships.existsByGroupIdAndUserId(groupId, userId)) {
            throw new ResourceNotFoundException("Group", groupId);
        }
    }

    public void requireOwner(UUID groupId, UUID userId) {
        requireMember(groupId, userId);
        if (!memberships.existsByGroupIdAndUserIdAndRole(groupId, userId, MembershipRole.OWNER)) {
            throw new ForbiddenException("Only the group owner can do this");
        }
    }
}
