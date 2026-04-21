package com.homie.finance.dto.statistic;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StatisticResponse implements Serializable {

    @Schema(description = "Tên danh mục", example = "Ăn uống")
    private String categoryName;

    @Schema(description = "Loại (INCOME/EXPENSE)", example = "EXPENSE")
    private String categoryType;

    @Schema(description = "Tổng tiền của danh mục này", example = "1500000")
    private Double totalAmount;
}