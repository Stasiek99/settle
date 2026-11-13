package dev.settle.settlement;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SettlementRepository extends JpaRepository<Settlement, UUID> {

    Page<Settlement> findByGroupId(UUID groupId, Pageable pageable);

    Optional<Settlement> findByIdAndGroupId(UUID id, UUID groupId);
}
