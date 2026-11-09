package dev.settle.group;

import java.time.Instant;
import java.util.UUID;

public record MembershipResponse(
        UUID id,
        UUID userId,
        String displayName,
        MembershipRole role,
        Instant joinedAt
) {

    public static MembershipResponse from(Membership membership) {
        return new MembershipResponse(
                membership.getId(),
                membership.getUser().getId(),
                membership.getUser().getDisplayName(),
                membership.getRole(),
                membership.getJoinedAt());
    }
}
