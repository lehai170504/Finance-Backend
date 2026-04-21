package com.homie.finance.dto.transaction;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class SetBudgetRequest {

    @NotBlank(message = "ID danh mục không được để trống")
    @Schema(description = "ID của Danh mục cần kiểm soát (Chỉ áp dụng cho EXPENSE)")
    private String categoryId;

    @Min(value = 1, message = "Tháng phải từ 1 đến 12")
    @Max(value = 12, message = "Tháng phải từ 1 đến 12")
    private int month;

    @Min(value = 2000, message = "Năm không hợp lệ")
    private int year;

    @Positive(message = "Hạn mức phải lớn hơn 0")
    @Schema(description = "Số tiền tối đa cho phép", example = "3000000")
    private Double limitAmount;
}