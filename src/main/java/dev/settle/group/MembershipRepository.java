package dev.settle.group;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    @EntityGraph(attributePaths = "user")
    List<Membership> findAllByGroupId(UUID groupId);

    @EntityGraph(attributePaths = "group")
    List<Membership> findAllByUserId(UUID userId);

    boolean existsByGroupIdAndUserId(UUID groupId, UUID userId);

    @Query("select m.user.id from Membership m where m.group.id = :groupId")
    Set<UUID> findUserIdsByGroupId(@Param("groupId") UUID groupId);
}
