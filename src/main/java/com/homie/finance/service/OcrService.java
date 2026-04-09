package com.homie.finance.service;

import com.homie.finance.dto.OcrResponse;
import com.homie.finance.utils.OcrUtils;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;

@Service
public class OcrService {

    @Autowired
    private CloudinaryService cloudinaryService;

    public OcrResponse analyzeReceipt(MultipartFile file) {
        // 1. Upload ảnh lên mây lấy link
        String receiptUrl = cloudinaryService.uploadImage(file);

        String extractedText = "";
        File tempFile = null;

        try {
            // 2. Chuyển MultipartFile thành File vật lý tạm thời để Tesseract đọc
            tempFile = Files.createTempFile("receipt-", "-" + file.getOriginalFilename()).toFile();
            file.transferTo(tempFile);

            // 3. Đánh thức Tesseract
            ITesseract tesseract = new Tesseract();
            // Trỏ đường dẫn vào thư mục chứa file ngôn ngữ (ngang hàng với src)
            tesseract.setDatapath("tessdata");
            // Set ngôn ngữ Tiếng Việt (chính là tên file vie.traineddata bỏ đuôi đi)
            tesseract.setLanguage("vie");

            // 4. Ma thuật bắt đầu: Đọc chữ từ ảnh
            extractedText = tesseract.doOCR(tempFile);

            // (Tùy chọn) In ra console xem nó đọc được gì để dễ debug
            System.out.println("Tesseract đọc được:\n" + extractedText);

        } catch (Exception e) {
            throw new RuntimeException("Tesseract bị lé rồi homie ơi: " + e.getMessage());
        } finally {
            // 5. QUAN TRỌNG: Đọc xong nhớ xóa file tạm đi để khỏi rác server
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }

        // 6. Đưa đống text vừa đọc được qua cho Regex xử lý
        Double amount = OcrUtils.extractAmount(extractedText);
        String storeName = OcrUtils.extractStoreName(extractedText);

        // 7. Trả kết quả đẹp về cho Frontend
        OcrResponse response = new OcrResponse();
        response.setAmount(amount);
        response.setSuggestedNote(storeName);
        response.setReceiptUrl(receiptUrl);

        return response;
    }
}