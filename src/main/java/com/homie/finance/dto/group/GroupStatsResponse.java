package com.homie.finance.dto.group;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
public class GroupStatsResponse {
    private Double totalExpense;
    private Map<String, Double> statsByCategory; // Tên danh mục -> Số tiền
    private Map<String, Double> statsByUser;     // Tên user -> Số tiền đã chi
}
