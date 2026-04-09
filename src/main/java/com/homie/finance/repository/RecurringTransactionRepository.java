package com.homie.finance.repository;

import com.homie.finance.entity.RecurringTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface RecurringTransactionRepository extends JpaRepository<RecurringTransaction, String> {

    /**
     * Tìm tất cả các giao dịch định kỳ thỏa mãn 2 điều kiện:
     * 1. Đang được bật (isActive = true)
     * 2. Ngày thực thi tiếp theo nhỏ hơn hoặc bằng ngày hiện tại (Đã đến hạn thu tiền)
     */
    List<RecurringTransaction> findByIsActiveTrueAndNextExecutionDateLessThanEqual(LocalDate date);

}