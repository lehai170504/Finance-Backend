package com.homie.finance.controller;

import com.homie.finance.dto.ApiResponse;
import com.homie.finance.entity.Category;
import com.homie.finance.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@Tag(name = "3. Category", description = "Quan ly cac nhom / phan loai tien te")
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    @GetMapping
    @Operation(summary = "Lay danh sach danh muc")
    public ApiResponse<List<Category>> getAllCategories() {
        return new ApiResponse<>(200, "Lay danh sach thanh cong!", categoryService.getAllCategories());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tao danh muc moi (Chi Admin)")
    public ApiResponse<Category> createCategory(@Valid @RequestBody Category category) {
        return new ApiResponse<>(201, "Tao danh muc moi thanh cong!", categoryService.createCategory(category));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cap nhat danh muc (Chi Admin)")
    public ApiResponse<Category> updateCategory(@PathVariable String id, @Valid @RequestBody Category category) {
        return new ApiResponse<>(200, "Cap nhat danh muc thanh cong!", categoryService.updateCategory(id, category));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Xoa danh muc (Chi Admin)")
    public ApiResponse<String> deleteCategory(@PathVariable String id) {
        categoryService.deleteCategory(id);
        return new ApiResponse<>(200, "Xoa danh muc thanh cong!", null);
    }
}
