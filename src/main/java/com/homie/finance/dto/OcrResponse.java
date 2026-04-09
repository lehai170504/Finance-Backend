package com.homie.finance.dto;

import lombok.Data;

@Data
public class OcrResponse {
    private Double amount;
    private String suggestedNote;
    private String receiptUrl;
}