package com.homie.finance.dto.category;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CategoryRequest {
    @NotBlank(message = "Tên danh mục không được để trống")
    private String name;

    @NotBlank(message = "Loại danh mục không được để trống (INCOME/EXPENSE)")
    private String type;

    private String icon;
}