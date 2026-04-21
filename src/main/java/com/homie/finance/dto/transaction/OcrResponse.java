package com.homie.finance.dto.transaction;

import lombok.Data;
import java.util.List;

@Data
public class OcrResponse {
    private Double totalAmount;
    private String suggestedNote;
    private String receiptUrl;
    private List<OcrItem> items;

    @Data
    public static class OcrItem {
        private String name;
        private Double amount;
        private String categorySuggestion;
    }
}