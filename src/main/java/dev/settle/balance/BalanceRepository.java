package dev.settle.balance;

import dev.settle.expense.Expense;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface BalanceRepository extends Repository<Expense, UUID> {

    @Query(value = """
            select t.user_id as "userId", t.currency as "currency", cast(sum(t.amount) as bigint) as "balanceMinor"
            from (
                select e.paid_by as user_id, e.currency as currency, e.amount_minor as amount
                from expenses e
                where e.group_id = :groupId
                union all
                select s.user_id as user_id, e.currency as currency, -s.amount_minor as amount
                from expense_splits s
                join expenses e on e.id = s.expense_id
                where e.group_id = :groupId
                union all
                select st.paid_by as user_id, st.currency as currency, st.amount_minor as amount
                from settlements st
                where st.group_id = :groupId
                union all
                select st.paid_to as user_id, st.currency as currency, -st.amount_minor as amount
                from settlements st
                where st.group_id = :groupId
            ) t
            group by t.user_id, t.currency
            """, nativeQuery = true)
    List<MemberNetBalance> findNetBalancesByGroupId(@Param("groupId") UUID groupId);
}
