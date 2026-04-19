package com.homie.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.homie.finance.dto.OcrResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
public class OcrService {

    @Autowired
    private CloudinaryService cloudinaryService;

    @Autowired
    private AiService aiService;

    public OcrResponse analyzeReceipt(MultipartFile file) {
        // 1. Upload lên Cloudinary để lưu trữ URL
        String receiptUrl = cloudinaryService.uploadImage(file);

        String prompt = "Bạn là chuyên gia kế toán. Hãy phân tích ảnh hóa đơn này và trả về JSON chính xác. " +
                "Nếu hóa đơn có nhiều món hàng, hãy liệt kê chi tiết từng món. " +
                "Cấu trúc JSON yêu cầu: " +
                "{" +
                "\"totalAmount\": số (tổng tiền)," +
                "\"suggestedNote\": \"ghi chú ngắn gọn tổng quát\"," +
                "\"items\": [" +
                "  {\"name\": \"tên món\", \"amount\": số tiền món đó, \"categorySuggestion\": \"tên danh mục phù hợp (ví dụ: Ăn uống, Di chuyển, Mua sắm...)\"}"
                +
                "]" +
                "}. " +
                "Lưu ý: Chỉ trả về JSON, không giải thích thêm.";

        try {
            // 2. Chuyển file sang Base64 để gửi cho AI
            String base64Image = Base64.getEncoder().encodeToString(file.getBytes());
            String mimeType = file.getContentType();

            // 3. Gọi bộ não AI xử lý
            JsonNode root = aiService.callGeminiAiRaw(prompt, base64Image, mimeType);

            OcrResponse response = new OcrResponse();
            response.setTotalAmount(root.path("totalAmount").asDouble(0.0));
            response.setSuggestedNote(root.path("suggestedNote").asText("Thanh toán hóa đơn"));

            List<OcrResponse.OcrItem> items = new ArrayList<>();
            JsonNode itemsNode = root.path("items");
            if (itemsNode.isArray()) {
                for (JsonNode node : itemsNode) {
                    OcrResponse.OcrItem item = new OcrResponse.OcrItem();
                    item.setName(node.path("name").asText());
                    item.setAmount(node.path("amount").asDouble(0.0));
                    item.setCategorySuggestion(node.path("categorySuggestion").asText());
                    items.add(item);
                }
            }
            response.setItems(items);
            response.setReceiptUrl(receiptUrl);

            return response;
        } catch (Exception e) {
            System.err.println("Gemini OCR Error: " + e.getMessage());
            OcrResponse fallback = new OcrResponse();
            fallback.setReceiptUrl(receiptUrl);
            fallback.setTotalAmount(0.0);
            fallback.setSuggestedNote("Không thể phân tích chi tiết hóa đơn");
            fallback.setItems(new ArrayList<>());
            return fallback;
        }
    }
}
