package com.homie.finance.job;

import com.homie.finance.entity.RecurringTransaction;
import com.homie.finance.entity.Transaction;
import com.homie.finance.entity.Wallet;
import com.homie.finance.repository.RecurringTransactionRepository;
import com.homie.finance.repository.TransactionRepository;
import com.homie.finance.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RecurringTransactionJob {

    private final RecurringTransactionRepository recurringRepository;
    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;

    // Chạy vào 00:01 mỗi ngày để quét các giao dịch định kỳ đến hạn
    @Scheduled(cron = "0 1 0 * * ?")
    @Transactional
    public void processRecurringTransactions() {
        System.out.println("⏳ [Recurring Job] Đang quét giao dịch định kỳ đến hạn...");
        LocalDate today = LocalDate.now();

        List<RecurringTransaction> dueTransactions = recurringRepository
                .findByIsActiveTrueAndNextExecutionDateLessThanEqual(today);

        for (RecurringTransaction rt : dueTransactions) {
            System.out.println("🔄 Xử lý giao dịch định kỳ: " + rt.getNote());

            // 1. Tạo Giao Dịch Mới
            Transaction t = new Transaction();
            t.setAmount(rt.getAmount());
            t.setNote("[Tự động] " + rt.getNote());
            t.setDate(today);
            t.setCategory(rt.getCategory());
            t.setWallet(rt.getWallet());
            t.setUser(rt.getUser());
            t.setDeleted(false);
            transactionRepository.save(t);

            // 2. Cập Nhật Số Dư Ví
            Wallet wallet = rt.getWallet();
            if ("INCOME".equalsIgnoreCase(rt.getCategory().getType())) {
                wallet.setBalance(wallet.getBalance() + rt.getAmount());
            } else {
                wallet.setBalance(wallet.getBalance() - rt.getAmount());
            }
            walletRepository.save(wallet);

            // 3. Tính Toán Ngày Đến Hạn Tiếp Theo
            LocalDate nextDate = rt.getNextExecutionDate();
            if ("MONTHLY".equalsIgnoreCase(rt.getFrequency())) {
                nextDate = nextDate.plusMonths(1);
            } else if ("WEEKLY".equalsIgnoreCase(rt.getFrequency())) {
                nextDate = nextDate.plusWeeks(1);
            } else {
                // Mặc định cộng 1 tháng nếu ko rõ
                nextDate = nextDate.plusMonths(1);
            }

            rt.setNextExecutionDate(nextDate);
            recurringRepository.save(rt);
        }

        System.out.println("✅ [Recurring Job] Hoàn thành xử lý " + dueTransactions.size() + " giao dịch.");
    }
}
