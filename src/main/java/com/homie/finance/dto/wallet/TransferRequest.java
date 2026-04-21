package com.homie.finance.dto.wallet;

import lombok.Data;

@Data
public class TransferRequest {
    private String fromId;
    private String toId;
    private Double amount;
}