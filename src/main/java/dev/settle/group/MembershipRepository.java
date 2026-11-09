package dev.settle.group;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    @EntityGraph(attributePaths = "user")
    List<Membership> findAllByGroupId(UUID groupId);

    @EntityGraph(attributePaths = "group")
    List<Membership> findAllByUserId(UUID userId);

    boolean existsByGroupIdAndUserId(UUID groupId, UUID userId);
}
