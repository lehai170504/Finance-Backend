package com.homie.finance.dto.wallet;

import lombok.Data;

@Data
public class WalletRequest {
    private String name;
    private Double balance;
    private String color;
}