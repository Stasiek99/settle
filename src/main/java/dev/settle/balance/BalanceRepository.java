package dev.settle.balance;

import dev.settle.expense.Expense;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface BalanceRepository extends Repository<Expense, UUID> {

    // Settlements join here as two more UNION ALL branches: +amount for the payer, -amount for the receiver.
    @Query(value = """
            select t.user_id as "userId", cast(sum(t.amount) as bigint) as "balanceMinor"
            from (
                select e.paid_by as user_id, e.amount_minor as amount
                from expenses e
                where e.group_id = :groupId
                union all
                select s.user_id as user_id, -s.amount_minor as amount
                from expense_splits s
                join expenses e on e.id = s.expense_id
                where e.group_id = :groupId
            ) t
            group by t.user_id
            """, nativeQuery = true)
    List<MemberNetBalance> findNetBalancesByGroupId(@Param("groupId") UUID groupId);
}
