package dev.settle.group;

import java.time.Instant;
import java.util.UUID;

public record GroupResponse(
        UUID id, String name, String currency, UUID createdBy, Instant createdAt, long version) {

    public static GroupResponse from(Group group) {
        return new GroupResponse(
                group.getId(),
                group.getName(),
                group.getCurrency(),
                group.getCreatedBy().getId(),
                group.getCreatedAt(),
                group.getVersion());
    }
}
