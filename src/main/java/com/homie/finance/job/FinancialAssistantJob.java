package com.homie.finance.job;

import com.homie.finance.entity.User;
import com.homie.finance.repository.TransactionRepository;
import com.homie.finance.repository.UserRepository;
import com.homie.finance.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Component
@RequiredArgsConstructor
public class FinancialAssistantJob {

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final EmailService emailService;

    // Lịch chuẩn: "0 0 8 1 * ?" -> Chạy vào đúng 8h00 sáng ngày mùng 1 hàng tháng.
    // Lịch để Test: "0 * * * * ?" -> Cứ ĐÚNG 1 PHÚT chạy 1 lần. (Test xong nhớ đổi lại lịch chuẩn nhé)

    @Scheduled(cron = "0 0 8 1 * ?")
    public void sendMonthlyFinancialReport() {
        System.out.println("🤖 Bot Trợ Lý Đang Thức Dậy! Quét data để gửi báo cáo...");

        // 1. Lấy ngày đầu và ngày cuối của THÁNG TRƯỚC
        LocalDate today = LocalDate.now();
        YearMonth lastMonth = YearMonth.from(today).minusMonths(1);
        LocalDate startDate = lastMonth.atDay(1);
        LocalDate endDate = lastMonth.atEndOfMonth();

        // 2. Lấy danh sách toàn bộ User
        List<User> users = userRepository.findAll();

        // 3. Quét từng người và tính toán
        for (User user : users) {
            if (user.getEmail() == null || user.getEmail().isEmpty()) continue;
            Double totalExpense = transactionRepository.sumTotalExpenseByUser(user, startDate, endDate);

            if (totalExpense == null) totalExpense = 0.0;

            // 4. Soạn Mail Báo Cáo
            String subject = "📊 Báo Cáo Tài Chính Tháng " + lastMonth.getMonthValue();

            // Format lại số tiền nhìn cho nó chuyên nghiệp homie nhé
            String formattedTotal = String.format("%,.0f", totalExpense);

            String body = "<h2>Chào " + user.getUsername() + ",</h2>" +
                    "<p>Tháng vừa qua (từ " + startDate + " đến " + endDate + "), bạn đã vung tay tiêu tốn hết:</p>" +
                    "<h3 style='color:red;'>" + formattedTotal + " VNĐ</h3>" +
                    "<p>Hãy mở App lên để xem chi tiết thống kê và cân đối lại tài chính tháng này nhé!</p>" +
                    "<p>Chúc homie một tháng mới rủng rỉnh tiền bạc! 💰</p>";

            // 5. Giao cho shipper đi gửi
            emailService.sendHtmlEmail(user.getEmail(), subject, body);
        }
        System.out.println("🤖 Bot Trợ Lý: Đã gửi xong báo cáo cho toàn bộ Server!");
    }
}