package com.homie.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.homie.finance.dto.StatisticResponse;
import com.homie.finance.entity.User;
import com.homie.finance.security.SecurityUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    /**
     * Lấy lời khuyên tài chính định kỳ (Dashboard)
     */
    public String getFinancialAdvice() {
        User currentUser = securityUtils.getCurrentUser();

        try {
            // 1. Thu thập dữ liệu ngữ cảnh đầy đủ
            String contextData = getFinancialContext();

            // 2. Kiểm tra API Key để quyết định luồng
            if (aiConfig.getApiKey() != null && !aiConfig.getApiKey().isBlank()) {
                String prompt = String.format(
                        "Bạn là 'Homie Financial AI', chuyên gia quản lý tài chính cá nhân.\n" +
                                "Dữ liệu thực tế của homie %s:\n%s\n" +
                                "Hãy phân tích và đưa ra lời khuyên ngắn gọn (dưới 150 từ), dùng các icon phù hợp, ngôn ngữ thân thiện, khích lệ. "
                                +
                                "Tập trung vào cân đối thu chi và mục tiêu tiết kiệm.",
                        currentUser.getUsername(), contextData);

                return callGeminiAiRaw(prompt, null);
            }

            return "🤖 Homie AI cần API Key để phân tích chuyên sâu. Nhưng nhìn sơ bộ, hãy cố gắng duy trì thói quen ghi chép nhé!";
        } catch (Exception e) {
            return "🤖 Homie AI đang bận xử lý dữ liệu một chút. Đừng lo, tài chính của bạn vẫn ổn! (Lỗi: "
                    + e.getMessage() + ")";
        }
    }

    /**
     * Chat tương tác với AI hỗ trợ History
     */
    public String chatWithAi(String userMessage, List<Map<String, String>> history) {
        User currentUser = securityUtils.getCurrentUser();
        String contextData = getFinancialContext();

        // System Instruction - Định hình tính cách AI
        String systemInstruction = String.format(
                "Bạn là 'Homie Financial AI' - người trợ lý tài chính thông minh, tận tâm và thân thiện của homie %s.\n"
                        +
                        "Dữ liệu tài chính tháng này của người dùng:\n%s\n" +
                        "QUY TẮC:\n" +
                        "1. Luôn trả lời dựa trên dữ liệu thực tế được cung cấp nếu câu hỏi liên quan đến tiền bạc.\n" +
                        "2. Ngôn ngữ: Tiếng Việt, trẻ trung, dùng 'homie', 'bạn' và các icon 💸, 🚀, 🐷.\n" +
                        "3. Ngắn gọn, súc tích, đi thẳng vào vấn đề.\n" +
                        "4. Nếu người dùng hỏi ngoài lề, hãy khéo léo dẫn dắt về việc quản lý tài chính.",
                currentUser.getUsername(), contextData);

        // Kết hợp System Instruction vào tin nhắn đầu tiên của Prompt nếu dùng Gemini
        // 1.5 Flash (hoặc dùng System Instruction API nếu có hỗ trợ)
        // Ở đây ta dùng cách đơn giản: chèn vào đầu chuỗi tin nhắn cuối
        String promptWithContext = "Bối cảnh: " + systemInstruction + "\n\nCâu hỏi: " + userMessage;

        if (aiConfig.getApiKey() != null && !aiConfig.getApiKey().isBlank()) {
            try {
                return callGeminiAiRaw(promptWithContext, history);
            } catch (Exception e) {
                return "Xin lỗi homie, bộ não AI của tôi đang hơi 'lag' một chút. Thử lại sau nhé! 😅";
            }
        }
        return "Tính năng chat yêu cầu Gemini API Key để hoạt động.";
    }

    /**
     * Thu thập dữ liệu tài chính chi tiết
     */
    private String getFinancialContext() {
        YearMonth now = YearMonth.now();
        List<StatisticResponse> stats = transactionService.getCategoryStatistics(now.atDay(1), now.atEndOfMonth());

        double totalExpense = stats.stream()
                .filter(s -> "EXPENSE".equals(s.getCategoryType()))
                .mapToDouble(StatisticResponse::getTotalAmount).sum();
        double totalIncome = stats.stream()
                .filter(s -> "INCOME".equals(s.getCategoryType()))
                .mapToDouble(StatisticResponse::getTotalAmount).sum();

        String categoryDetails = stats.stream()
                .filter(s -> "EXPENSE".equals(s.getCategoryType()))
                .map(s -> s.getCategoryName() + ": " + String.format("%,.0f", s.getTotalAmount()) + "đ")
                .collect(Collectors.joining(", "));

        List<com.homie.finance.dto.SavingsGoalResponse> goals = savingsGoalService.getMyGoals();
        String goalsSummary = goals.stream()
                .map(g -> String.format("- %s: %,.0f/%,.0f (%s%%)", g.getName(), g.getSavedAmount(),
                        g.getTargetAmount(), g.getProgressPercent()))
                .collect(Collectors.joining("\n"));

        return String.format(
                "- Tổng thu: %,.0fđ\n" +
                        "- Tổng chi: %,.0fđ\n" +
                        "- Chi tiết: %s\n" +
                        "- Mục tiêu tiết kiệm:\n%s",
                totalIncome, totalExpense, categoryDetails, goalsSummary);
    }

    private String callGeminiAiRaw(String prompt, List<Map<String, String>> history) {
        if (aiConfig.getApiKey() == null || aiConfig.getApiKey().isBlank()) {
            return "Homie ơi, chưa có API Key nên mình chưa 'thông thái' được. Hãy thiết lập API Key nhé!";
        }

        String url = aiConfig.getApiUrl() + "?key=" + aiConfig.getApiKey();
        GeminiRequest request = new GeminiRequest();

        if (history != null) {
            for (Map<String, String> msg : history) {
                String role = "user".equalsIgnoreCase(msg.get("role")) ? "user" : "model";
                request.getContents().add(new Content(role, msg.get("content")));
            }
        }
        request.getContents().add(new Content("user", prompt));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<GeminiRequest> entity = new HttpEntity<>(request, headers);

        try {
            // Sử dụng JsonNode để linh hoạt hơn trong việc đọc Response
            JsonNode response = restTemplate.postForObject(url, entity, JsonNode.class);

            if (response != null && response.has("candidates")) {
                JsonNode candidates = response.get("candidates");
                if (candidates.isArray() && candidates.size() > 0) {
                    return candidates.get(0)
                            .path("content")
                            .path("parts").get(0)
                            .path("text").asText();
                }
            }

            System.err.println("Gemini Response lạ: " + response);
            return "Gemini trả về kết quả trống hoặc bị chặn nội dung rồi homie!";

        } catch (org.springframework.web.client.HttpClientErrorException.TooManyRequests e) {
            return "Homie dùng 'hao' quá, Gemini bảo là hết lượt miễn phí rồi. Chờ chút nhé! ⏳";
        } catch (Exception e) {
            System.err.println("Lỗi kết nối Gemini: " + e.getMessage());
            return "Không thể kết nối với não bộ AI. Homie kiểm tra mạng hoặc API Key nhé!";
        }
    }

    // --- Gemini API DTOs ---
    @Data
    static class GeminiRequest {
        private List<Content> contents = new ArrayList<>();
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class Content {
        private String role; // "user" or "model"
        private List<Part> parts = new ArrayList<>();

        public Content(String role, String text) {
            this.role = role;
            this.parts.add(new Part(text));
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class Part {
        private String text;
    }
}
