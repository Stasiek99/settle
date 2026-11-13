package dev.settle.expense;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    Page<Expense> findByGroupId(UUID groupId, Pageable pageable);

    @EntityGraph(attributePaths = "splits")
    Optional<Expense> findByIdAndGroupId(UUID id, UUID groupId);

}
