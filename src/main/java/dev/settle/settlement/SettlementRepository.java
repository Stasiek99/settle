package dev.settle.settlement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SettlementRepository extends JpaRepository<Settlement, UUID> {

    List<Settlement> findAllByGroupIdOrderBySettledOnDescCreatedAtDesc(UUID groupId);

    Optional<Settlement> findByIdAndGroupId(UUID id, UUID groupId);
}
