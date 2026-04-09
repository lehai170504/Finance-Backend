package com.homie.finance.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

@Data
public class TransactionResponse implements Serializable {
    private String id;
    private Double amount;
    private String note;
    private LocalDate date;
    private String categoryName;
    private String categoryType;
    private String receiptUrl;
    private String walletName;
    private String groupId;
    private String groupName;
    private String userId;
    private String userName;
}