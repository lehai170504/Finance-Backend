package com.homie.finance.job;

import com.homie.finance.dto.TransactionRequest;
import com.homie.finance.entity.RecurringTransaction;
import com.homie.finance.repository.RecurringTransactionRepository;
import com.homie.finance.service.TransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class TransactionCronJob {

    @Autowired
    private RecurringTransactionRepository recurringRepository;

    @Autowired
    private TransactionService transactionService;

    // Chạy vào lúc 00:00:00 mỗi ngày
    @Scheduled(cron = "0 0 0 * * ?")
    public void executeRecurringTransactions() {
        LocalDate today = LocalDate.now();

        // 1. Tìm tất cả các giao dịch định kỳ ĐANG BẬT và ĐẾN HẠN
        List<RecurringTransaction> dueTransactions = recurringRepository
                .findByIsActiveTrueAndNextExecutionDateLessThanEqual(today);

        for (RecurringTransaction template : dueTransactions) {
            try {
                // 2. Tạo Request giả lập
                TransactionRequest request = new TransactionRequest();
                request.setAmount(template.getAmount());
                request.setNote("[TỰ ĐỘNG] " + template.getNote());

                request.setDate(today);

                // 3. Gọi Service chuyên dụng cho Bot
                transactionService.createSystemTransaction(
                        template.getWallet().getId(),
                        template.getCategory().getId(),
                        template.getUser(),
                        request);

                // 4. Tính toán ngày trừ tiền cho lần kế tiếp
                if ("WEEKLY".equals(template.getFrequency())) {
                    template.setNextExecutionDate(today.plusWeeks(1));
                } else {
                    // Mặc định MONTHLY nếu null hoặc không xác định
                    template.setNextExecutionDate(today.plusMonths(1));
                }

                recurringRepository.save(template);
                System.out.println("✅ Đã xử lý tự động giao dịch: " + template.getNote());

            } catch (Exception e) {
                System.err.println("❌ Lỗi khi chạy giao dịch tự động " + template.getId() + ": " + e.getMessage());
            }
        }
    }
}