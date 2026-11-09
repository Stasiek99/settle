package dev.settle.group;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface GroupRepository extends JpaRepository<Group, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Group g where g.id = :id")
    Optional<Group> findByIdForUpdate(@Param("id") UUID id);

    @Modifying(flushAutomatically = true)
    @Query("update Group g set g.rotationCursor = g.rotationCursor + :units where g.id = :id")
    int advanceRotationCursor(@Param("id") UUID id, @Param("units") long units);
}
