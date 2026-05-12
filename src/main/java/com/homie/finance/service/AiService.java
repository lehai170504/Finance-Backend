package com.homie.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.homie.finance.dto.statistic.StatisticResponse;
import com.homie.finance.dto.goal.SavingsGoalResponse;
import com.homie.finance.entity.User;
import com.homie.finance.security.SecurityUtils;
import com.homie.finance.config.AiConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private final TransactionService transactionService;
    private final SavingsGoalService savingsGoalService;
    private final AiConfig aiConfig;
    private final RestTemplate restTemplate;
    private final SecurityUtils securityUtils;

    // Hàm tạo Headers có chứa API Key để bảo mật đường truyền tới Python
    // Microservice
    private HttpHeaders createHeadersWithApiKey() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String apiKey = aiConfig.getServiceApiKey();
        if (apiKey != null && !apiKey.isEmpty()) {
            headers.set("X-API-Key", apiKey);
        }
        return headers;
    }

    private String buildUrl(String endpoint) {
        String baseUrl = aiConfig.getApiUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + endpoint;
    }

    /**
     * Lấy lời khuyên tài chính định kỳ (Dashboard)
     */
    public String getFinancialAdvice() {
        try {
            User currentUser = securityUtils.getCurrentUser();
            String contextData = getFinancialContext();
            String url = buildUrl("/api/ai/advice");

            Map<String, Object> request = new HashMap<>();
            request.put("username", currentUser.getUsername());
            request.put("financial_context", contextData);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, createHeadersWithApiKey());

            log.info("Đang lấy lời khuyên AI cho user: {}", currentUser.getUsername());
            JsonNode response = restTemplate.postForObject(url, entity, JsonNode.class);

            return response != null ? response.get("answer").asText() : "AI không có phản hồi.";

        } catch (Exception e) {
            log.error("❌ Lỗi AI Advice: {}", e.getMessage());
            return "Homie AI đang bận bảo trì bộ não Python một chút. Thử lại sau nhé!";
        }
    }

    /**
     * Chat tương tác với AI hỗ trợ History
     */
    public String chatWithAi(String userMessage, List<Map<String, String>> history) {
        try {
            User currentUser = securityUtils.getCurrentUser();
            String contextData = getFinancialContext();
            String url = buildUrl("/api/ai/chat");

            Map<String, Object> request = new HashMap<>();
            request.put("username", currentUser.getUsername());
            request.put("user_message", userMessage);
            request.put("financial_context", contextData);
            request.put("history", history);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, createHeadersWithApiKey());

            log.info("Chatting with AI: user={}", currentUser.getUsername());
            JsonNode response = restTemplate.postForObject(url, entity, JsonNode.class);

            return response != null ? response.get("answer").asText() : "AI im lặng một cách lạ thường...";

        } catch (Exception e) {
            log.error("Lỗi AI Chat: {}", e.getMessage());
            return "Xin lỗi homie, bộ não AI Python đang hơi 'lag'. Thử lại sau nhé! 😅";
        }
    }

    /**
     * Gọi OCR để phân tích hóa đơn từ hình ảnh
     */
    public JsonNode callGeminiAiRaw(String prompt, String base64Image, String mimeType) {
        try {
            String url = buildUrl("/api/ai/ocr");

            Map<String, Object> request = new HashMap<>();
            request.put("image_base64", base64Image);
            request.put("mime_type", mimeType);
            request.put("prompt", prompt);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, createHeadersWithApiKey());

            return restTemplate.postForObject(url, entity, JsonNode.class);

        } catch (Exception e) {
            log.error("Lỗi gọi OCR Microservice: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Tổng hợp dữ liệu tài chính cá nhân để làm "nguyên liệu" cho AI phân tích
     */
    private String getFinancialContext() {
        YearMonth now = YearMonth.now();
        List<StatisticResponse> stats = transactionService.getCategoryStatistics(now.atDay(1), now.atEndOfMonth());
        if (stats == null)
            stats = List.of();

        double totalExpense = stats.stream()
                .filter(s -> "EXPENSE".equals(s.getType()))
                .mapToDouble(s -> s.getTotalAmount() != null ? s.getTotalAmount() : 0.0).sum();
        double totalIncome = stats.stream()
                .filter(s -> "INCOME".equals(s.getType()))
                .mapToDouble(s -> s.getTotalAmount() != null ? s.getTotalAmount() : 0.0).sum();

        String categoryDetails = stats.stream()
                .filter(s -> "EXPENSE".equals(s.getType()))
                .map(s -> String.format("%s: %,.0fđ", s.getCategoryName(),
                        s.getTotalAmount() != null ? s.getTotalAmount() : 0.0))
                .collect(Collectors.joining(", "));

        List<SavingsGoalResponse> goals = savingsGoalService.getMyGoals();
        String goalsSummary = goals.stream()
                .map(g -> String.format("- %s: %,.0f/%,.0f (%s%%)", g.getName(), g.getSavedAmount(),
                        g.getTargetAmount(), g.getProgressPercent()))
                .collect(Collectors.joining("\n"));

        // Sử dụng Text Block giúp chuỗi nhìn rất chuyên nghiệp
        return """
                Báo cáo tài chính tháng %d/%d:
                - Tổng thu: %,.0fđ
                - Tổng chi: %,.0fđ
                - Chi tiết chi tiêu: %s
                - Các mục tiêu tiết kiệm hiện tại:
                %s
                """.formatted(now.getMonthValue(), now.getYear(), totalIncome, totalExpense, categoryDetails,
                goalsSummary);
    }
}