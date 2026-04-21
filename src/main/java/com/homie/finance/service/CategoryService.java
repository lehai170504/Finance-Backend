package com.homie.finance.service;

import com.homie.finance.dto.category.CategoryRequest;
import com.homie.finance.dto.category.CategoryResponse;
import com.homie.finance.entity.Category;
import com.homie.finance.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor // 🔥 Tối ưu 1: Constructor Injection
public class CategoryService {

    private final CategoryRepository categoryRepository;

    // 1. Lấy tất cả danh mục (Cache lại list DTO, siêu nhanh và an toàn)
    @Cacheable("categories")
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    // 2. Tạo danh mục mới
    @Transactional
    @CacheEvict(value = "categories", allEntries = true) // Xóa cache cũ đi khi có data mới
    public CategoryResponse createCategory(CategoryRequest request) {
        Category category = new Category();
        category.setName(request.getName());
        category.setType(request.getType());
        category.setIcon(request.getIcon());

        return mapToDto(categoryRepository.save(category));
    }

    // 3. Cập nhật danh mục
    @Transactional
    @CacheEvict(value = "categories", allEntries = true)
    public CategoryResponse updateCategory(String id, CategoryRequest request) {
        Category existing = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Danh mục không tồn tại!"));

        existing.setName(request.getName());
        existing.setType(request.getType());
        existing.setIcon(request.getIcon());

        return mapToDto(categoryRepository.save(existing));
    }

    // 4. Xóa danh mục (Soft Delete)
    @Transactional
    @CacheEvict(value = "categories", allEntries = true)
    public void deleteCategory(String id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy danh mục để xóa!"));

        category.setDeleted(true);
        categoryRepository.save(category);
    }

    // --- MAPPER ---
    private CategoryResponse mapToDto(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .type(category.getType())
                .icon(category.getIcon())
                .build();
    }
}