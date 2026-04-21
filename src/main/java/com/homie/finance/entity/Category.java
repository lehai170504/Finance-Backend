package com.homie.finance.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Entity
@Table(name = "categories")
@Data
@NoArgsConstructor
@AllArgsConstructor
@org.hibernate.annotations.SQLRestriction("is_deleted = false")
public class Category implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Schema(description = "Mã định danh bảo mật UUID", example = "550e8400-e29b-41d4-a716-446655440000", accessMode = Schema.AccessMode.READ_ONLY)
    private String id;

    @NotBlank(message = "Tên danh mục không được để trống đâu homie!")
    @Schema(description = "Tên của danh mục", example = "Ăn uống")
    private String name;

    @NotBlank(message = "Loại danh mục (INCOME/EXPENSE) là bắt buộc!")
    @Schema(description = "Phân loại: INCOME (Thu) hoặc EXPENSE (Chi)", example = "EXPENSE")
    private String type;

    @NotBlank(message = "Chọn một cái icon cho chất nhé homie!")
    @Schema(description = "Tên định danh của Icon", example = "ic_fastfood")
    @Column(nullable = false, columnDefinition = "varchar(255) default 'category'")
    private String icon;

    @Column(name = "is_deleted", nullable = false, columnDefinition = "boolean default false")
    private boolean isDeleted = false;

    public Category(String name, String type, String icon) {
        this.name = name;
        this.type = type;
        this.icon = icon;
        this.isDeleted = false;
    }
}
