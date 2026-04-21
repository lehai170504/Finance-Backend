package com.homie.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.homie.finance.dto.transaction.OcrResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OcrService {

    private final CloudinaryService cloudinaryService;
    private final AiService aiService;

    public OcrResponse analyzeReceipt(MultipartFile file) {
        // 1. Upload lên Cloudinary để lưu trữ và lấy URL (Dùng làm bằng chứng giao dịch)
        String receiptUrl = cloudinaryService.uploadImage(file);

        // Prompt được tinh chỉnh để ép AI trả về đúng định dạng mong muốn
        String prompt = """
                Bạn là chuyên gia kế toán. Hãy phân tích ảnh hóa đơn này và trả về JSON chính xác.
                Nếu hóa đơn có nhiều món hàng, hãy liệt kê chi tiết từng món.
                Cấu trúc JSON yêu cầu:
                {
                  "totalAmount": số (tổng tiền),
                  "suggestedNote": "ghi chú ngắn gọn tổng quát",
                  "items": [
                    {"name": "tên món", "amount": số tiền món đó, "categorySuggestion": "tên danh mục phù hợp (Ăn uống, Di chuyển, Mua sắm...)"}
                  ]
                }.
                Lưu ý: Chỉ trả về JSON, không giải thích thêm.
                """;

        try {
            // 2. Chuyển file sang Base64 để gửi cho AI xử lý hình ảnh (Multimodal)
            String base64Image = Base64.getEncoder().encodeToString(file.getBytes());
            String mimeType = file.getContentType();

            // 3. Gọi "bộ não" AI (Gemini) xử lý
            log.info("Đang gửi hóa đơn lên Gemini AI để phân tích...");
            JsonNode root = aiService.callGeminiAiRaw(prompt, base64Image, mimeType);

            // 4. Map dữ liệu từ JSON AI trả về sang DTO Response
            OcrResponse response = new OcrResponse();
            response.setTotalAmount(root.path("totalAmount").asDouble(0.0));
            response.setSuggestedNote(root.path("suggestedNote").asText("Thanh toán hóa đơn"));
            response.setReceiptUrl(receiptUrl);

            List<OcrResponse.OcrItem> items = new ArrayList<>();
            JsonNode itemsNode = root.path("items");
            if (itemsNode.isArray()) {
                for (JsonNode node : itemsNode) {
                    OcrResponse.OcrItem item = new OcrResponse.OcrItem();
                    item.setName(node.path("name").asText("Món hàng không tên"));
                    item.setAmount(node.path("amount").asDouble(0.0));
                    item.setCategorySuggestion(node.path("categorySuggestion").asText("Khác"));
                    items.add(item);
                }
            }
            response.setItems(items);

            log.info("Phân tích hóa đơn thành công. Tổng tiền: {}", response.getTotalAmount());
            return response;

        } catch (Exception e) {
            // 5. Fallback logic: Nếu AI "ngáo" hoặc lỗi mạng, vẫn trả về URL ảnh để User tự điền tay
            log.error("Lỗi Gemini OCR: {}. Trả về dữ liệu trống kèm URL ảnh.", e.getMessage());

            OcrResponse fallback = new OcrResponse();
            fallback.setReceiptUrl(receiptUrl);
            fallback.setTotalAmount(0.0);
            fallback.setSuggestedNote("Không thể phân tích chi tiết hóa đơn (User tự điền)");
            fallback.setItems(new ArrayList<>());
            return fallback;
        }
    }
}