package com.homie.finance.dto.goal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SavingsGoalRequest {
    private String name;
    private Double targetAmount;
    private String icon;
    private String color;
}
