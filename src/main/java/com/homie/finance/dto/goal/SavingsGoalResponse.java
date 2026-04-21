package com.homie.finance.dto.goal;

import lombok.Data;

@Data
public class SavingsGoalResponse {
    private String id;
    private String name;
    private Double targetAmount;
    private Double savedAmount;
    private Double progressPercent; // % hoàn thành (0-100)
    private String icon;
    private String color;
    private boolean completed;
}
