package com.homie.finance.job;

import com.homie.finance.entity.Notification;
import com.homie.finance.entity.RecurringTransaction;
import com.homie.finance.entity.Transaction;
import com.homie.finance.entity.Wallet;
import com.homie.finance.repository.NotificationRepository;
import com.homie.finance.repository.RecurringTransactionRepository;
import com.homie.finance.repository.TransactionRepository;
import com.homie.finance.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class RecurringTransactionJob {

    private final RecurringTransactionRepository recurringRepository;
    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final NotificationRepository notificationRepository;

    @Scheduled(cron = "0 1 0 * * ?")
    @Transactional
    public void processRecurringTransactions() {
        log.info("⏳ [Recurring Job] Đang quét giao dịch định kỳ đến hạn...");
        LocalDate today = LocalDate.now();

        List<RecurringTransaction> dueTransactions = recurringRepository
                .findByIsActiveTrueAndNextExecutionDateLessThanEqual(today);

        int successCount = 0;
        int failCount = 0;

        for (RecurringTransaction rt : dueTransactions) {
            try {
                Wallet wallet = rt.getWallet();

                if (wallet == null || wallet.isDeleted()) {
                    log.warn("Ví của giao dịch định kỳ {} đã bị xóa!", rt.getId());
                    failCount++;
                    continue;
                }

                String categoryType = rt.getCategory() != null ? rt.getCategory().getType() : "EXPENSE";
                if ("EXPENSE".equalsIgnoreCase(categoryType) && wallet.getBalance() < rt.getAmount()) {
                    log.error("Không đủ số dư cho giao dịch định kỳ: {} - Số dư: {}, Cần: {}",
                            rt.getNote(), wallet.getBalance(), rt.getAmount());

                    Notification notification = new Notification(rt.getUser(),
                            "Giao dịch định kỳ '" + rt.getNote() + "' thất bại! Số dư không đủ.");
                    notificationRepository.save(notification);

                    rt.setNextExecutionDate(rt.getNextExecutionDate().plusMonths(1));
                    recurringRepository.save(rt);
                    failCount++;
                    continue;
                }

                Transaction t = new Transaction();
                t.setAmount(rt.getAmount());
                t.setNote("[Tự động] " + rt.getNote());
                t.setDate(today);
                t.setCategory(rt.getCategory());
                t.setWallet(wallet);
                t.setUser(rt.getUser());
                t.setDeleted(false);
                transactionRepository.save(t);

                if ("INCOME".equalsIgnoreCase(categoryType)) {
                    wallet.setBalance(wallet.getBalance() + rt.getAmount());
                } else {
                    wallet.setBalance(wallet.getBalance() - rt.getAmount());
                }
                walletRepository.save(wallet);

                LocalDate nextDate = rt.getNextExecutionDate();
                if ("MONTHLY".equalsIgnoreCase(rt.getFrequency())) {
                    nextDate = nextDate.plusMonths(1);
                } else if ("WEEKLY".equalsIgnoreCase(rt.getFrequency())) {
                    nextDate = nextDate.plusWeeks(1);
                } else {
                    nextDate = nextDate.plusMonths(1);
                }

                rt.setNextExecutionDate(nextDate);
                recurringRepository.save(rt);
                successCount++;

                log.info("Đã xử lý giao dịch định kỳ: {}", rt.getNote());

            } catch (Exception e) {
                log.error("Lỗi khi xử lý giao dịch định kỳ {}: {}", rt.getId(), e.getMessage());
                failCount++;
            }
        }

        log.info("[Recurring Job] Hoàn thành: {} thành công, {} thất bại", successCount, failCount);
    }
}
