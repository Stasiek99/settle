package dev.settle.group;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @EntityGraph(attributePaths = "group")
    Page<Membership> findByUserId(UUID userId, Pageable pageable);

    boolean existsByGroupIdAndUserId(UUID groupId, UUID userId);

    boolean existsByGroupIdAndUserIdAndRole(UUID groupId, UUID userId, MembershipRole role);

    @Query("select m.user.id from Membership m where m.group.id = :groupId")
    Set<UUID> findUserIdsByGroupId(@Param("groupId") UUID groupId);
}
