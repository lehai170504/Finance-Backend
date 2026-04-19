package com.homie.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.homie.finance.dto.StatisticResponse;
import com.homie.finance.entity.User;
import com.homie.finance.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
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
     * Lấy lời khuyên tài chính định kỳ (Dashboard) - Gọi qua Python Microservice
     */
    public String getFinancialAdvice() {
        User currentUser = securityUtils.getCurrentUser();
        String contextData = getFinancialContext();

        try {
            String baseUrl = aiConfig.getApiUrl();
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            String url = baseUrl + "/api/ai/advice";

            Map<String, Object> request = new HashMap<>();
            request.put("username", currentUser.getUsername());
            request.put("financial_context", contextData);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            JsonNode response = restTemplate.postForObject(url, entity, JsonNode.class);
            return response.get("answer").asText();

        } catch (Exception e) {
            return "🤖 Homie AI đang bận bảo trì bộ não Python một chút. (Lỗi: " + e.getMessage() + ")";
        }
    }

    /**
     * Chat tương tác với AI hỗ trợ History - Gọi qua Python Microservice
     */
    public String chatWithAi(String userMessage, List<Map<String, String>> history) {
        User currentUser = securityUtils.getCurrentUser();
        String contextData = getFinancialContext();

        try {
            String baseUrl = aiConfig.getApiUrl();
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            String url = baseUrl + "/api/ai/chat";

            Map<String, Object> request = new HashMap<>();
            request.put("username", currentUser.getUsername());
            request.put("user_message", userMessage);
            request.put("financial_context", contextData);
            request.put("history", history);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            JsonNode response = restTemplate.postForObject(url, entity, JsonNode.class);
            return response.get("answer").asText();

        } catch (Exception e) {
            return "Xin lỗi homie, bộ não AI Python đang hơi 'lag'. Thử lại sau nhé! 😅";
        }
    }

    public JsonNode callGeminiAiRaw(String prompt, String base64Image, String mimeType) {
        try {
            String baseUrl = aiConfig.getApiUrl();
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            String url = baseUrl + "/api/ai/ocr";

            Map<String, Object> request = new HashMap<>();
            request.put("image_base64", base64Image);
            request.put("mime_type", mimeType);
            request.put("prompt", prompt);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            return restTemplate.postForObject(url, entity, JsonNode.class);

        } catch (Exception e) {
            System.err.println("Lỗi gọi OCR Microservice: " + e.getMessage());
            return null;
        }
    }

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
                        "- Chi tiết chi tiêu: %s\n" +
                        "- Các mục tiêu tiết kiệm:\n%s",
                totalIncome, totalExpense, categoryDetails, goalsSummary);
    }
}
