package com.homie.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.homie.finance.config.AiConfig;
import com.homie.finance.dto.goal.SavingsGoalResponse;
import com.homie.finance.dto.statistic.StatisticResponse;
import com.homie.finance.entity.User;
import com.homie.finance.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private static final String AI_ADVICE_ENDPOINT = "/api/ai/advice";
    private static final String AI_CHAT_ENDPOINT = "/api/ai/chat";
    private static final String AI_OCR_ENDPOINT = "/api/ai/ocr";

    private final TransactionService transactionService;
    private final SavingsGoalService savingsGoalService;
    private final AiConfig aiConfig;
    private final RestTemplate restTemplate;
    private final SecurityUtils securityUtils;

    private HttpHeaders createHeadersWithApiKey() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String serviceApiKey = aiConfig.getServiceApiKey();

        if (serviceApiKey == null || serviceApiKey.trim().isEmpty()) {
            throw new IllegalStateException("AI_SERVICE_API_KEY chưa được cấu hình ở Backend.");
        }

        headers.set("X-API-Key", serviceApiKey.trim());
        return headers;
    }

    private String buildUrl(String endpoint) {
        String baseUrl = aiConfig.getApiUrl();

        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalStateException("AI_SERVICE_URL chưa được cấu hình.");
        }

        baseUrl = baseUrl.trim();

        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        if (!endpoint.startsWith("/")) {
            endpoint = "/" + endpoint;
        }

        return baseUrl + endpoint;
    }

    private String extractAnswer(JsonNode response, String fallbackMessage) {
        if (response == null || response.isNull()) {
            return fallbackMessage;
        }

        JsonNode answerNode = response.get("answer");
        if (answerNode != null && !answerNode.isNull() && !answerNode.asText().trim().isEmpty()) {
            return answerNode.asText();
        }

        JsonNode adviceNode = response.get("advice");
        if (adviceNode != null && !adviceNode.isNull() && !adviceNode.asText().trim().isEmpty()) {
            return adviceNode.asText();
        }

        JsonNode detailNode = response.get("detail");
        if (detailNode != null && !detailNode.isNull() && !detailNode.asText().trim().isEmpty()) {
            log.warn("AI service trả về detail thay vì answer: {}", detailNode.asText());
        }

        return fallbackMessage;
    }

    public String getFinancialAdvice() {
        try {
            User currentUser = securityUtils.getCurrentUser();
            String contextData = getFinancialContext();
            String url = buildUrl(AI_ADVICE_ENDPOINT);

            Map<String, Object> request = new HashMap<>();
            request.put("username", safeUsername(currentUser));
            request.put("financial_context", contextData);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, createHeadersWithApiKey());

            log.info("Đang lấy lời khuyên AI cho user={}", safeUsername(currentUser));

            JsonNode response = restTemplate.postForObject(url, entity, JsonNode.class);

            return extractAnswer(response, "AI không có phản hồi.");

        } catch (HttpClientErrorException.Forbidden e) {
            log.error("❌ AI Advice bị từ chối 403. Kiểm tra AI_SERVICE_API_KEY giữa Backend và AI Service.", e);
            return "Homie AI chưa xác thực được với backend. Kiểm tra AI_SERVICE_API_KEY nhé!";
        } catch (RestClientException e) {
            log.error("❌ Lỗi kết nối AI Advice service: {}", e.getMessage(), e);
            return "Homie AI đang bận bảo trì bộ não Python một chút. Thử lại sau nhé!";
        } catch (Exception e) {
            log.error("❌ Lỗi AI Advice: {}", e.getMessage(), e);
            return "Homie AI đang gặp lỗi khi phân tích tài chính. Thử lại sau nhé!";
        }
    }

    public String chatWithAi(String userMessage, List<Map<String, String>> history) {
        try {
            User currentUser = securityUtils.getCurrentUser();
            String contextData = getFinancialContext();
            String url = buildUrl(AI_CHAT_ENDPOINT);

            Map<String, Object> request = new HashMap<>();
            request.put("username", safeUsername(currentUser));
            request.put("user_message", userMessage == null ? "" : userMessage);
            request.put("financial_context", contextData);
            request.put("history", history == null ? List.of() : history);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, createHeadersWithApiKey());

            log.info("Đang chat với AI cho user={}", safeUsername(currentUser));

            JsonNode response = restTemplate.postForObject(url, entity, JsonNode.class);

            return extractAnswer(response, "AI im lặng một cách lạ thường...");

        } catch (HttpClientErrorException.Forbidden e) {
            log.error("❌ AI Chat bị từ chối 403. Kiểm tra AI_SERVICE_API_KEY giữa Backend và AI Service.", e);
            return "Homie AI chưa xác thực được với backend. Kiểm tra AI_SERVICE_API_KEY nhé!";
        } catch (RestClientException e) {
            log.error("❌ Lỗi kết nối AI Chat service: {}", e.getMessage(), e);
            return "Xin lỗi homie, bộ não AI Python đang hơi lag. Thử lại sau nhé! 😅";
        } catch (Exception e) {
            log.error("❌ Lỗi AI Chat: {}", e.getMessage(), e);
            return "Xin lỗi homie, AI đang gặp lỗi khi xử lý tin nhắn. Thử lại sau nhé! 😅";
        }
    }

    public JsonNode callGeminiAiRaw(String prompt, String base64Image, String mimeType) {
        try {
            String url = buildUrl(AI_OCR_ENDPOINT);

            Map<String, Object> request = new HashMap<>();
            request.put("image_base64", base64Image == null ? "" : base64Image);
            request.put("mime_type", normalizeMimeType(mimeType));
            request.put("prompt", prompt);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, createHeadersWithApiKey());

            log.info("Đang gọi AI OCR service.");

            return restTemplate.postForObject(url, entity, JsonNode.class);

        } catch (HttpClientErrorException.Forbidden e) {
            log.error("❌ AI OCR bị từ chối 403. Kiểm tra AI_SERVICE_API_KEY giữa Backend và AI Service.", e);
            return null;
        } catch (RestClientException e) {
            log.error("❌ Lỗi kết nối OCR Microservice: {}", e.getMessage(), e);
            return null;
        } catch (Exception e) {
            log.error("❌ Lỗi gọi OCR Microservice: {}", e.getMessage(), e);
            return null;
        }
    }

    private String getFinancialContext() {
        YearMonth now = YearMonth.now();

        List<StatisticResponse> stats = getSafeCategoryStatistics(now);
        List<SavingsGoalResponse> goals = getSafeSavingsGoals();

        double totalExpense = stats.stream()
                .filter(s -> "EXPENSE".equalsIgnoreCase(nullToEmpty(s.getType())))
                .mapToDouble(this::safeTotalAmount)
                .sum();

        double totalIncome = stats.stream()
                .filter(s -> "INCOME".equalsIgnoreCase(nullToEmpty(s.getType())))
                .mapToDouble(this::safeTotalAmount)
                .sum();

        String categoryDetails = stats.stream()
                .filter(s -> "EXPENSE".equalsIgnoreCase(nullToEmpty(s.getType())))
                .map(s -> String.format(
                        "%s: %,.0fđ",
                        nullToDefault(s.getCategoryName(), "Không rõ"),
                        safeTotalAmount(s)
                ))
                .collect(Collectors.joining(", "));

        if (categoryDetails.isBlank()) {
            categoryDetails = "Chưa có dữ liệu chi tiêu";
        }

        String goalsSummary = goals.stream()
                .filter(Objects::nonNull)
                .map(g -> String.format(
                        "- %s: %,.0f/%,.0f (%s%%)",
                        nullToDefault(g.getName(), "Mục tiêu không tên"),
                        safeDouble(g.getSavedAmount()),
                        safeDouble(g.getTargetAmount()),
                        g.getProgressPercent() == null ? "0" : g.getProgressPercent().toString()
                ))
                .collect(Collectors.joining("\n"));

        if (goalsSummary.isBlank()) {
            goalsSummary = "- Chưa có mục tiêu tiết kiệm nào";
        }

        return """
                Báo cáo tài chính tháng %d/%d:
                - Tổng thu: %,.0fđ
                - Tổng chi: %,.0fđ
                - Chênh lệch thu - chi: %,.0fđ
                - Chi tiết chi tiêu: %s
                - Các mục tiêu tiết kiệm hiện tại:
                %s
                """.formatted(
                now.getMonthValue(),
                now.getYear(),
                totalIncome,
                totalExpense,
                totalIncome - totalExpense,
                categoryDetails,
                goalsSummary
        );
    }

    private List<StatisticResponse> getSafeCategoryStatistics(YearMonth now) {
        try {
            List<StatisticResponse> stats = transactionService.getCategoryStatistics(
                    now.atDay(1),
                    now.atEndOfMonth()
            );

            return stats == null ? List.of() : stats;

        } catch (Exception e) {
            log.error("❌ Không lấy được dữ liệu thống kê cho AI context: {}", e.getMessage(), e);
            return List.of();
        }
    }

    private List<SavingsGoalResponse> getSafeSavingsGoals() {
        try {
            List<SavingsGoalResponse> goals = savingsGoalService.getMyGoals();
            return goals == null ? List.of() : goals;

        } catch (Exception e) {
            log.error("❌ Không lấy được savings goals cho AI context: {}", e.getMessage(), e);
            return List.of();
        }
    }

    private String safeUsername(User user) {
        if (user == null || user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            return "homie";
        }

        return user.getUsername();
    }

    private double safeTotalAmount(StatisticResponse response) {
        if (response == null || response.getTotalAmount() == null) {
            return 0.0;
        }

        return response.getTotalAmount();
    }

    private double safeDouble(Double value) {
        return value == null ? 0.0 : value;
    }

    private String normalizeMimeType(String mimeType) {
        if (mimeType == null || mimeType.trim().isEmpty()) {
            return "image/png";
        }

        return mimeType.trim();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String nullToDefault(String value, String defaultValue) {
        return value == null || value.trim().isEmpty() ? defaultValue : value;
    }
}