package com.homie.finance.repository;

import com.homie.finance.entity.Debt;
import com.homie.finance.entity.GroupSpace;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DebtRepository extends JpaRepository<Debt, String> {
    List<Debt> findByGroupIdAndIsSettledFalse(String groupId);

    List<Debt> findByTransaction(com.homie.finance.entity.Transaction transaction);

    boolean existsByGroupAndDebtorAndIsSettledFalse(com.homie.finance.entity.GroupSpace group,
            com.homie.finance.entity.User debtor);

    boolean existsByGroupAndCreditorAndIsSettledFalse(com.homie.finance.entity.GroupSpace group,
            com.homie.finance.entity.User creditor);

    void deleteByGroup(GroupSpace group);
}