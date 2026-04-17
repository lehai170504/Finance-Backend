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
        String receiptUrl = cloudinaryService.uploadImage(file);

        String extractedText = "";
        File tempFile = null;

        try {
            tempFile = Files.createTempFile("receipt-", "-" + file.getOriginalFilename()).toFile();
            file.transferTo(tempFile);

            ITesseract tesseract = new Tesseract();
            tesseract.setDatapath("tessdata");
            tesseract.setLanguage("vie");

            extractedText = tesseract.doOCR(tempFile);
        } catch (Exception e) {
            if (receiptUrl != null && !receiptUrl.isBlank()) {
                try {
                    cloudinaryService.deleteImage(receiptUrl);
                } catch (Exception ignored) {
                }
            }
            throw new RuntimeException("OCR that bai: " + e.getMessage());
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }

        Double amount = OcrUtils.extractAmount(extractedText);
        String storeName = OcrUtils.extractStoreName(extractedText);

        OcrResponse response = new OcrResponse();
        response.setAmount(amount);
        response.setSuggestedNote(storeName);
        response.setReceiptUrl(receiptUrl);

        return response;
    }
}
