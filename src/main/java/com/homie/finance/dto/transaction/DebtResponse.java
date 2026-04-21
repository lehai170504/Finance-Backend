package com.homie.finance.dto.transaction;

import lombok.Data;

@Data
public class DebtResponse {
    private String id;
    private Double amount;
    private String creditorName;
    private String debtorName;
    private boolean isSettled;
}