package dev.settle.expense;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    @EntityGraph(attributePaths = "splits")
    List<Expense> findAllByGroupIdOrderByExpenseDateDescCreatedAtDesc(UUID groupId);

    @EntityGraph(attributePaths = "splits")
    Optional<Expense> findByIdAndGroupId(UUID id, UUID groupId);
}
