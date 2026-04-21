package com.homie.finance.dto.transaction;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class BulkTransactionRequest {
    private String walletId;
    private String groupId;
    private LocalDate date;
    private String receiptUrl;
    private List<TransactionItem> items;

    @Data
    public static class TransactionItem {
        private String categoryId;
        private Double amount;
        private String note;
    }
}
