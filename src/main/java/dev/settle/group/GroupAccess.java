package dev.settle.group;

import dev.settle.common.ForbiddenException;
import dev.settle.common.ResourceNotFoundException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class GroupAccess {

    private final MembershipRepository memberships;
    private final MembershipLookup membershipLookup;

    GroupAccess(MembershipRepository memberships, MembershipLookup membershipLookup) {
        this.memberships = memberships;
        this.membershipLookup = membershipLookup;
    }

    public void requireMember(UUID groupId, UUID userId) {
        if (!membershipLookup.isMember(groupId, userId)) {
            throw new ResourceNotFoundException("Group", groupId);
        }
    }

    public boolean isOwner(UUID groupId, UUID userId) {
        return memberships.existsByGroupIdAndUserIdAndRole(groupId, userId, MembershipRole.OWNER);
    }

    public void requireOwner(UUID groupId, UUID userId) {
        requireMember(groupId, userId);
        if (!memberships.existsByGroupIdAndUserIdAndRole(groupId, userId, MembershipRole.OWNER)) {
            throw new ForbiddenException("Only the group owner can do this");
        }
    }
}
