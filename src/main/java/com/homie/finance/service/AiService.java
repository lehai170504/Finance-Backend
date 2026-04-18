package com.homie.finance.service;

import com.homie.finance.dto.StatisticResponse;
import com.homie.finance.entity.User;
import com.homie.finance.security.SecurityUtils;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AiService {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private SavingsGoalService savingsGoalService;

    @Autowired
    private com.homie.finance.config.AiConfig aiConfig;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private SecurityUtils securityUtils;

    public String getFinancialAdvice() {
        User currentUser = securityUtils.getCurrentUser();

        try {
            // 1. Thu thập dữ liệu ngữ cảnh
            YearMonth now = YearMonth.now();
            List<StatisticResponse> stats = transactionService.getCategoryStatistics(now.atDay(1), now.atEndOfMonth());

            double totalExpense = stats.stream()
                    .filter(s -> "EXPENSE".equals(s.getCategoryType()))
                    .mapToDouble(StatisticResponse::getTotalAmount).sum();
            double totalIncome = stats.stream()
                    .filter(s -> "INCOME".equals(s.getCategoryType()))
                    .mapToDouble(StatisticResponse::getTotalAmount).sum();

            List<com.homie.finance.dto.SavingsGoalResponse> goals = savingsGoalService.getMyGoals();
            String goalsSummary = goals.stream()
                    .map(g -> String.format("- %s: Đã có %,.0f/%,.0f (%s%%)", g.getName(), g.getSavedAmount(),
                            g.getTargetAmount(), g.getProgressPercent()))
                    .collect(Collectors.joining("\n"));

            // 2. Kiểm tra API Key để quyết định luồng
            if (aiConfig.getApiKey() != null && !aiConfig.getApiKey().isBlank()) {
                return callGeminiAi(currentUser.getUsername(), totalIncome, totalExpense, stats, goalsSummary);
            }

            return generateAdviceLocally(currentUser.getUsername(), totalIncome, totalExpense, stats, goalsSummary);
        } catch (Exception e) {
            return "🤖 Homie AI đang bận xử lý dữ liệu một chút. Đừng lo, tài chính của bạn vẫn đang được theo dõi sát sao! (Lỗi: "
                    + e.getMessage() + ")";
        }
    }

    private String callGeminiAi(String name, double income, double expense, List<StatisticResponse> stats,
            String goals) {
        String url = aiConfig.getApiUrl() + "?key=" + aiConfig.getApiKey();

        // Build Prompt chuyên nghiệp
        String categoryDetails = stats.stream()
                .filter(s -> "EXPENSE".equals(s.getCategoryType()))
                .map(s -> s.getCategoryName() + ": " + String.format("%,.0f", s.getTotalAmount()) + "đ")
                .collect(Collectors.joining(", "));

        String promptText = String.format(
                "Bạn là 'Homie Financial AI', chuyên gia quản lý tài chính cá nhân. Hãy phân tích dữ liệu tháng này của homie %s:\n"
                        +
                        "- Thu nhập: %,.0f VNĐ\n" +
                        "- Chi tiêu: %,.0f VNĐ\n" +
                        "- Chi tiết chi tiêu: %s\n" +
                        "- Mục tiêu tiết kiệm:\n%s\n\n" +
                        "Hãy đưa ra lời khuyên ngắn gọn (dưới 150 từ), sử dụng các icon phù hợp, ngôn ngữ thân thiện, khích lệ. "
                        +
                        "Tập trung vào việc cân đối thu chi và đạt mục tiêu tiết kiệm nhanh nhất.",
                name, income, expense, categoryDetails, goals);

        // Request DTO (Sử dụng inner classes phía dưới)
        GeminiRequest request = new GeminiRequest(promptText);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<GeminiRequest> entity = new HttpEntity<>(request, headers);

        try {
            GeminiResponse response = restTemplate.postForObject(url, entity, GeminiResponse.class);
            if (response != null && response.getCandidates() != null && !response.getCandidates().isEmpty()) {
                return response.getCandidates().get(0).getContent().getParts().get(0).getText();
            }
        } catch (Exception e) {
            System.err.println("Gemini API Error: " + e.getMessage());
        }

        return generateAdviceLocally(name, income, expense, stats, goals)
                + "\n\n(Lưu ý: Đang dùng AI dự phòng do sự cố kết nối)";
    }

    private String generateAdviceLocally(String name, double income, double expense, List<StatisticResponse> stats,
            String goals) {
        StringBuilder sb = new StringBuilder();
        sb.append("🤖 [Homie Local AI]\nChào ").append(name).append("! ");
        double balance = income - expense;

        if (balance < 0) {
            sb.append("⚠️ Bạn đang chi tiêu vượt mức thu nhập. Hãy xem xét cắt giảm các khoản chi không thiết yếu.");
        } else if (income > 0 && expense < income * 0.5) {
            sb.append(
                    "🌟 Tuyệt vời! Bạn đang kiểm soát tài chính rất tốt. Đừng quên trích thêm vào quỹ tiết kiệm nhé.");
        } else {
            sb.append("✅ Tài chính của bạn đang ở mức ổn định. Tiếp tục duy trì nhé!");
        }

        if (!goals.isEmpty() && balance > 0) {
            sb.append(
                    "\n\n🐷 Gợi ý: Với số dư hiện tại, homie có thể nạp thêm một chút vào mục tiêu tiết kiệm để nhanh về đích!");
        }

        return sb.toString();
    }

    public String chatWithAi(String userMessage) {
        User currentUser = securityUtils.getCurrentUser();

        // Lấy ngữ cảnh nhanh
        YearMonth now = YearMonth.now();
        List<StatisticResponse> stats = transactionService.getCategoryStatistics(now.atDay(1), now.atEndOfMonth());
        double totalExpense = stats.stream().filter(s -> "EXPENSE".equals(s.getCategoryType()))
                .mapToDouble(StatisticResponse::getTotalAmount).sum();
        double totalIncome = stats.stream().filter(s -> "INCOME".equals(s.getCategoryType()))
                .mapToDouble(StatisticResponse::getTotalAmount).sum();

        String prompt = String.format(
                "Bạn là 'Homie Financial AI'. Homie %s vừa hỏi: '%s'.\n" +
                        "Ngữ cảnh tài chính tháng này:\n" +
                        "- Thu nhập: %,.0f VNĐ, Chi tiêu: %,.0f VNĐ.\n" +
                        "Hãy trả lời homie một cách thông minh, ngắn gọn và hữu ích dựa trên câu hỏi và dữ liệu này.",
                currentUser.getUsername(), userMessage, totalIncome, totalExpense);

        if (aiConfig.getApiKey() != null && !aiConfig.getApiKey().isBlank()) {
            try {
                return callGeminiAiRaw(prompt);
            } catch (Exception e) {
                return "Xin lỗi homie, tôi đang gặp chút vấn đề về kết nối. Thử lại sau nhé!";
            }
        }
        return "Tính năng chat yêu cầu Gemini API Key. Tuy nhiên dựa trên dữ liệu, tôi thấy bạn đang có số dư là "
                + (totalIncome - totalExpense) + "đ.";
    }

    private String callGeminiAiRaw(String prompt) {
        String url = aiConfig.getApiUrl() + "?key=" + aiConfig.getApiKey();
        GeminiRequest request = new GeminiRequest(prompt);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<GeminiRequest> entity = new HttpEntity<>(request, headers);

        GeminiResponse response = restTemplate.postForObject(url, entity, GeminiResponse.class);
        if (response != null && response.getCandidates() != null && !response.getCandidates().isEmpty()) {
            return response.getCandidates().get(0).getContent().getParts().get(0).getText();
        }
        return "AI không phản hồi, thử lại sau nhé!";
    }

    // --- Gemini API DTOs ---
    @Data
    static class GeminiRequest {
        private List<Content> contents = new ArrayList<>();

        public GeminiRequest(String text) {
            Content content = new Content();
            Part part = new Part();
            part.setText(text);
            content.getParts().add(part);
            this.contents.add(content);
        }
    }

    @Data
    static class GeminiResponse {
        private List<Candidate> candidates;

        @Data
        static class Candidate {
            private Content content;
        }
    }

    @Data
    static class Content {
        private List<Part> parts = new ArrayList<>();
    }

    @Data
    static class Part {
        private String text;
    }
}
