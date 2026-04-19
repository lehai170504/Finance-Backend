package com.homie.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homie.finance.config.AiConfig;
import com.homie.finance.dto.OcrResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
public class OcrService {

    @Autowired
    private CloudinaryService cloudinaryService;

    @Autowired
    private AiConfig aiConfig;

    @Autowired
    private RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public OcrResponse analyzeReceipt(MultipartFile file) {
        // 1. Upload lên Cloudinary để lưu trữ
        String receiptUrl = cloudinaryService.uploadImage(file);

        try {
            // 2. Chuyển ảnh sang Base64 để gửi cho Gemini
            String base64Image = Base64.getEncoder().encodeToString(file.getBytes());
            String mimeType = file.getContentType();

            // 3. Gọi Gemini AI Vision
            String jsonResult = callGeminiVision(base64Image, mimeType);

            // 4. Parse kết quả JSON từ Gemini
            JsonNode root = objectMapper.readTree(jsonResult);

            OcrResponse response = new OcrResponse();
            response.setAmount(root.path("amount").asDouble());
            response.setSuggestedNote(root.path("storeName").asText("Hóa đơn từ Gemini"));
            response.setReceiptUrl(receiptUrl);

            return response;

        } catch (Exception e) {
            System.err.println("Gemini OCR Error: " + e.getMessage());
            OcrResponse fallback = new OcrResponse();
            fallback.setReceiptUrl(receiptUrl);
            fallback.setSuggestedNote("Không thể phân tích tự động");
            return fallback;
        }
    }

    private String callGeminiVision(String base64Data, String mimeType) {
        String url = aiConfig.getApiUrl() + "?key=" + aiConfig.getApiKey();

        String prompt = "Bạn là chuyên gia đọc hóa đơn. Hãy phân tích ảnh hóa đơn này và trích xuất thông tin sau dưới dạng JSON:\n"
                +
                "{\n" +
                "  \"amount\": (tổng số tiền thanh toán, kiểu số),\n" +
                "  \"storeName\": \"(tên cửa hàng hoặc thương hiệu)\"\n" +
                "}\n" +
                "Chỉ trả về JSON, không thêm văn bản khác.";

        GeminiVisionRequest request = new GeminiVisionRequest();
        Content content = new Content();
        content.getParts().add(new Part(prompt));
        content.getParts().add(new Part(new InlineData(mimeType, base64Data)));
        request.getContents().add(content);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<GeminiVisionRequest> entity = new HttpEntity<>(request, headers);

        try {
            JsonNode response = restTemplate.postForObject(url, entity, JsonNode.class);
            if (response != null) {
                String rawText = response.path("candidates").get(0).path("content").path("parts").get(0).path("text")
                        .asText();
                return rawText.replace("```json", "").replace("```", "").trim();
            }
        } catch (Exception e) {
            throw new RuntimeException("Lỗi kết nối Gemini AI: " + e.getMessage());
        }
        return "{}";
    }

    @Data
    static class GeminiVisionRequest {
        private List<Content> contents = new ArrayList<>();
    }

    @Data
    static class Content {
        private List<Part> parts = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Part {
        private String text;
        private InlineData inlineData;

        public Part(String text) {
            this.text = text;
        }

        public Part(InlineData inlineData) {
            this.inlineData = inlineData;
        }
    }

    @Data
    @AllArgsConstructor
    static class InlineData {
        private String mimeType;
        private String data;
    }
}
