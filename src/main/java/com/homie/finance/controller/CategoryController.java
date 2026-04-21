package com.homie.finance.controller;

import com.homie.finance.dto.category.CategoryRequest;
import com.homie.finance.dto.category.CategoryResponse;
import com.homie.finance.dto.format.ApiResponse;
import com.homie.finance.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Tag(name = "3. Category", description = "Quản lý các nhóm / phân loại tiền tệ")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @Operation(summary = "Lấy danh sách danh mục")
    public ApiResponse<List<CategoryResponse>> getAllCategories() {
        return new ApiResponse<>(200, "Lấy danh sách thành công!", categoryService.getAllCategories());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tạo danh mục mới (Chỉ Admin)")
    public ApiResponse<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest request) {
        return new ApiResponse<>(201, "Tạo danh mục mới thành công!", categoryService.createCategory(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cập nhật danh mục (Chỉ Admin)")
    public ApiResponse<CategoryResponse> updateCategory(@PathVariable String id, @Valid @RequestBody CategoryRequest request) {
        return new ApiResponse<>(200, "Cập nhật danh mục thành công!", categoryService.updateCategory(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Xóa danh mục (Chỉ Admin)")
    public ApiResponse<String> deleteCategory(@PathVariable String id) {
        categoryService.deleteCategory(id);
        return new ApiResponse<>(200, "Xóa danh mục thành công!", null);
    }
}