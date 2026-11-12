package dev.settle.group;

import dev.settle.cache.CacheNames;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class MembershipLookup {

    private final MembershipRepository memberships;

    MembershipLookup(MembershipRepository memberships) {
        this.memberships = memberships;
    }

    @Cacheable(cacheNames = CacheNames.MEMBERSHIPS, key = "#groupId.toString() + ':' + #userId", unless = "!#result")
    public boolean isMember(UUID groupId, UUID userId) {
        return memberships.existsByGroupIdAndUserId(groupId, userId);
    }
}
